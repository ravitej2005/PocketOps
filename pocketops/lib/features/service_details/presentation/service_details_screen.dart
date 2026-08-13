import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';
import 'package:pocketops/features/infrastructure/presentation/infrastructure_providers.dart';

class ServiceDetailsScreen extends ConsumerStatefulWidget {
  const ServiceDetailsScreen({required this.infrastructure, super.key});

  final InfrastructureSummary infrastructure;

  @override
  ConsumerState<ServiceDetailsScreen> createState() =>
      _ServiceDetailsScreenState();
}

class _ServiceDetailsScreenState extends ConsumerState<ServiceDetailsScreen> {
  final List<ContainerMetricUpdate> _metrics = [];
  List<InfrastructureResource>? _resources;
  InfrastructureSummary? _infrastructure;
  WebSocket? _socket;
  Timer? _uptimeTicker;
  Object? _error;
  String? _selectedResourceId;
  bool _live = false;
  int _tick = 0;
  bool _actionInProgress = false;
  String? _actionError;

  @override
  void initState() {
    super.initState();
    _infrastructure = widget.infrastructure;
    _uptimeTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      // Only rebuild for the uptime counter when the selected resource is RUNNING.
      final selected = _resources?.firstWhere(
        (r) => r.externalResourceId == _selectedResourceId,
        orElse: () => _resources!.first,
      );
      if (selected != null && selected.status == 'RUNNING') {
        setState(() => _tick++);
      }
    });
    Future<void>.microtask(_loadAndConnect);
  }

  @override
  void dispose() {
    _socket?.close();
    _uptimeTicker?.cancel();
    super.dispose();
  }

  Future<void> _loadAndConnect() async {
    final repository = ref.read(infrastructureRepositoryProvider);
    try {
      final resources = await repository.resources(widget.infrastructure.id);
      if (!mounted) {
        return;
      }
      setState(() => _resources = resources);
      final uri = await repository.updatesStreamUri(widget.infrastructure.id);
      final socket = await WebSocket.connect(uri.toString());
      if (!mounted) {
        await socket.close();
        return;
      }
      setState(() {
        _socket = socket;
        _live = true;
      });
      socket.listen(
        _handleMessage,
        onDone: () {
          if (mounted) {
            setState(() => _live = false);
          }
        },
        onError: (_) {
          if (mounted) {
            setState(() => _live = false);
          }
        },
      );
    } catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    }
  }

  void _handleMessage(dynamic message) {
    final json = jsonDecode(message as String) as Map<String, dynamic>;
    switch (json['type']) {
      case 'MetricUpdate':
        final update = ContainerMetricUpdate.fromJson(json);
        setState(() {
          _metrics.add(update);
          if (_metrics.length > 60) {
            _metrics.removeRange(0, _metrics.length - 60);
          }
          _applyMetricStartedAt(update);
        });
      case 'ResourceStateChanged':
        final update = ResourceStateUpdate.fromJson(json);
        setState(() => _applyResourceState(update));
      case 'InfrastructureStateChanged':
        final update = InfrastructureStateUpdate.fromJson(json);
        setState(() {
          _infrastructure = _infrastructure?.copyWith(
            healthStatus: update.healthStatus,
          );
        });
    }
  }

  void _applyResourceState(ResourceStateUpdate update) {
    final resources = _resources;
    if (resources == null) return;
    final index = resources.indexWhere(
      (item) => item.externalResourceId == update.resourceId,
    );
    final next = InfrastructureResource(
      id: index >= 0 ? resources[index].id : update.resourceId,
      externalResourceId: update.resourceId,
      displayName: update.displayName,
      resourceType: update.resourceType,
      status: update.status,
      criticality: update.criticality,
      lastSeenAt: update.lastSeenAt,
      startedAt: update.startedAt,
    );
    _resources =
        index >= 0
            ? [...resources.take(index), next, ...resources.skip(index + 1)]
            : [...resources, next];
    // When the selected resource stops, clear its stale metrics so the
    // panel shows 'Waiting for live metrics' rather than old readings.
    if (update.resourceId == _selectedResourceId && update.status != 'RUNNING') {
      _metrics.removeWhere((m) => m.resourceId == update.resourceId);
    }
  }

  void _applyMetricStartedAt(ContainerMetricUpdate update) {
    if (update.startedAt == null || _resources == null) {
      return;
    }
    _resources =
        _resources!
            .map(
              (item) =>
                  item.externalResourceId == update.resourceId
                      ? item.copyWith(startedAt: update.startedAt)
                      : item,
            )
            .toList();
  }

  Future<void> _executeAction(String action) async {
    final selected = _resources!.firstWhere(
      (item) => item.externalResourceId == _selectedResourceId,
    );

    final isCritical = selected.criticality == 'CRITICAL';
    final actionLabel = action.replaceAll('_CONTAINER', '').toLowerCase();

    // Show confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _ActionConfirmationDialog(
        action: actionLabel,
        resourceName: selected.displayName,
        isCritical: isCritical,
      ),
    );

    if (confirmed != true) {
      return;
    }

    // Biometric gate — required if device supports it; if not available, still allow action.
    final localAuth = LocalAuthentication();
    final canCheckBiometrics = await localAuth.canCheckBiometrics;
    final isDeviceSupported = await localAuth.isDeviceSupported();
    if (canCheckBiometrics || isDeviceSupported) {
      final authenticated = await localAuth.authenticate(
        localizedReason: 'Confirm $actionLabel of ${selected.displayName}',
        options: const AuthenticationOptions(
          biometricOnly: false,
          stickyAuth: true,
        ),
      );
      if (!authenticated) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Authentication failed or cancelled.')),
        );
        return;
      }
    }

    // Execute action
    setState(() {
      _actionInProgress = true;
      _actionError = null;
    });

    try {
      final repository = ref.read(infrastructureRepositoryProvider);
      await repository.executeAction(
        infrastructureId: widget.infrastructure.id,
        resourceId: _selectedResourceId!,
        action: action,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$actionLabel initiated for ${selected.displayName}')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _actionError = e.toString());
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Action failed: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _actionInProgress = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    final resources = _resources;
    if (error != null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.infrastructure.name)),
        body: _CenteredMessage(
          icon: Icons.cloud_off,
          message: '$error',
          action: TextButton.icon(
            onPressed: () {
              setState(() {
                _error = null;
                _resources = null;
              });
              _loadAndConnect();
            },
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
          ),
        ),
      );
    }
    if (resources == null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.infrastructure.name)),
        body: const _ResourceSkeleton(),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(widget.infrastructure.name)),
      body: _body(resources),
    );
  }

  Widget _body(List<InfrastructureResource> items) {
    if (items.isEmpty) {
      return _CenteredMessage(
        icon: Icons.dns,
        message: 'No resources discovered yet',
        action: TextButton.icon(
          onPressed: _loadAndConnect,
          icon: const Icon(Icons.refresh),
          label: const Text('Refresh'),
        ),
      );
    }
    _selectedResourceId ??= items.first.externalResourceId;
    final selected = items.firstWhere(
      (item) => item.externalResourceId == _selectedResourceId,
      orElse: () => items.first,
    );
    final selectedMetrics =
        _metrics
            .where((metric) => metric.resourceId == selected.externalResourceId)
            .toList();

    final capabilities = widget.infrastructure.capabilities;
    final canStart = capabilities.contains('START');
    final canStop = capabilities.contains('STOP');
    final canRestart = capabilities.contains('RESTART');

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _HealthHeader(infrastructure: _infrastructure!, live: _live),
        const SizedBox(height: 12),
        ...items.map(
          (item) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _ResourceTile(
              item: item,
              selected: item.externalResourceId == selected.externalResourceId,
              onTap:
                  () => setState(
                    () => _selectedResourceId = item.externalResourceId,
                  ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        _MetricsPanel(
          resource: selected,
          metrics: selectedMetrics,
          tick: _tick,
        ),
        const SizedBox(height: 12),
        _ActionsPanel(
          resource: selected,
          canStart: canStart,
          canStop: canStop,
          canRestart: canRestart,
          inProgress: _actionInProgress,
          error: _actionError,
          onAction: _executeAction,
        ),
      ],
    );
  }
}

class _HealthHeader extends StatelessWidget {
  const _HealthHeader({required this.infrastructure, required this.live});

  final InfrastructureSummary infrastructure;
  final bool live;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(live ? Icons.sensors : Icons.sensors_off),
        title: Text(infrastructure.healthStatus),
        subtitle: Text(live ? 'LIVE' : 'STALE'),
      ),
    );
  }
}

class _ResourceTile extends StatelessWidget {
  const _ResourceTile({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final InfrastructureResource item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: selected ? Theme.of(context).colorScheme.primaryContainer : null,
      child: ListTile(
        onTap: onTap,
        leading: const Icon(Icons.view_in_ar),
        title: Text(item.displayName),
        subtitle: Text('${item.status} - ${item.resourceType}'),
        trailing: Text(item.criticality),
      ),
    );
  }
}

class _MetricsPanel extends StatelessWidget {
  const _MetricsPanel({
    required this.resource,
    required this.metrics,
    required this.tick,
  });

  final InfrastructureResource resource;
  final List<ContainerMetricUpdate> metrics;
  final int tick;

  @override
  Widget build(BuildContext context) {
    final latest = metrics.isEmpty ? null : metrics.last;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              resource.displayName,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            if (latest == null)
              const Text('Waiting for live metrics')
            else ...[
              _MetricRow(
                label: 'CPU',
                value: '${latest.cpuPercent.toStringAsFixed(1)}%',
              ),
              _MetricRow(
                label: 'Memory',
                value:
                    '${_mb(latest.memoryUsageBytes)} / ${_mb(latest.memoryLimitBytes)} MB',
              ),
              _MetricRow(
                label: 'Network in',
                value: '${_mb(latest.networkRxBytes)} MB',
              ),
              _MetricRow(
                label: 'Network out',
                value: '${_mb(latest.networkTxBytes)} MB',
              ),
              _MetricRow(label: 'Uptime', value: '${_uptimeSeconds()}s'),
              const SizedBox(height: 12),
              SizedBox(
                height: 72,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children:
                      metrics.take(24).map((metric) {
                        final height =
                            metric.cpuPercent.clamp(2, 100).toDouble();
                        return Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 1),
                            child: FractionallySizedBox(
                              heightFactor: height / 100,
                              alignment: Alignment.bottomCenter,
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  color: Theme.of(context).colorScheme.primary,
                                  borderRadius: BorderRadius.circular(2),
                                ),
                              ),
                            ),
                          ),
                        );
                      }).toList(),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  int _uptimeSeconds() {
    final startedAt =
        resource.startedAt ?? (metrics.isEmpty ? null : metrics.last.startedAt);
    if (resource.status != 'RUNNING' || startedAt == null) {
      return 0;
    }
    return DateTime.now().difference(startedAt).inSeconds;
  }

  String _mb(int bytes) => (bytes / 1024 / 1024).toStringAsFixed(1);
}

class _MetricRow extends StatelessWidget {
  const _MetricRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Text(value, style: Theme.of(context).textTheme.labelLarge),
        ],
      ),
    );
  }
}

class _CenteredMessage extends StatelessWidget {
  const _CenteredMessage({
    required this.icon,
    required this.message,
    required this.action,
  });

  final IconData icon;
  final String message;
  final Widget action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            action,
          ],
        ),
      ),
    );
  }
}

class _ResourceSkeleton extends StatelessWidget {
  const _ResourceSkeleton();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemBuilder:
          (_, __) =>
              const Card(child: SizedBox(height: 76, width: double.infinity)),
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemCount: 5,
    );
  }
}

class _ActionsPanel extends StatelessWidget {
  const _ActionsPanel({
    required this.resource,
    required this.canStart,
    required this.canStop,
    required this.canRestart,
    required this.inProgress,
    required this.error,
    required this.onAction,
  });

  final InfrastructureResource resource;
  final bool canStart;
  final bool canStop;
  final bool canRestart;
  final bool inProgress;
  final String? error;
  final Future<void> Function(String) onAction;

  @override
  Widget build(BuildContext context) {
    final actions = <Widget>[];
    
    if (canStart && resource.status != 'RUNNING') {
      actions.add(_ActionButton(
        label: 'Start',
        icon: Icons.play_arrow,
        color: Colors.green,
        onPressed: inProgress ? null : () => onAction('START_CONTAINER'),
      ));
    }
    
    if (canStop && resource.status == 'RUNNING') {
      actions.add(_ActionButton(
        label: 'Stop',
        icon: Icons.stop,
        color: Colors.orange,
        onPressed: inProgress ? null : () => onAction('STOP_CONTAINER'),
      ));
    }
    
    if (canRestart) {
      actions.add(_ActionButton(
        label: 'Restart',
        icon: Icons.restart_alt,
        color: Colors.blue,
        onPressed: inProgress ? null : () => onAction('RESTART_CONTAINER'),
      ));
    }

    if (actions.isEmpty) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Actions', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: actions,
            ),
            if (error != null) ...[
              const SizedBox(height: 12),
              Text(
                'Error: $error',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.error),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.label,
    required this.icon,
    required this.color,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final Color color;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      onPressed: onPressed,
      icon: Icon(icon),
      label: Text(label),
      style: FilledButton.styleFrom(
        backgroundColor: color,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      ),
    );
  }
}

class _ActionConfirmationDialog extends StatelessWidget {
  const _ActionConfirmationDialog({
    required this.action,
    required this.resourceName,
    required this.isCritical,
  });

  final String action;
  final String resourceName;
  final bool isCritical;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('${action[0].toUpperCase()}${action.substring(1)} $resourceName?'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('This will $action the container "$resourceName".'),
          if (isCritical) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.errorContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: Theme.of(context).colorScheme.error),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'This resource is marked CRITICAL. $action may impact dependent services.',
                      style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 8),
          Text(
            'Biometric authentication will be required to proceed.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: Text('${action[0].toUpperCase()}${action.substring(1)}'),
        ),
      ],
    );
  }
}
