import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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

  @override
  void initState() {
    super.initState();
    _infrastructure = widget.infrastructure;
    _uptimeTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
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
    if (resources == null) {
      return;
    }
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
