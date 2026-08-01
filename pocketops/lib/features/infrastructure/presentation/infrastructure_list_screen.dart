import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';
import 'package:pocketops/features/infrastructure/presentation/infrastructure_providers.dart';

class InfrastructureListScreen extends ConsumerWidget {
  const InfrastructureListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final infrastructures = ref.watch(infrastructureListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Infrastructures')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showCreateSheet(context, ref),
        child: const Icon(Icons.add),
      ),
      body: infrastructures.when(
        loading: () => const _SkeletonList(),
        error:
            (error, _) => _CenteredMessage(
              icon: Icons.cloud_off,
              message: '$error',
              action: TextButton.icon(
                onPressed: () => ref.invalidate(infrastructureListProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            ),
        data: (items) {
          if (items.isEmpty) {
            return _CenteredMessage(
              icon: Icons.add_circle_outline,
              message: 'Add your first infrastructure',
              action: FilledButton.icon(
                onPressed: () => _showCreateSheet(context, ref),
                icon: const Icon(Icons.add),
                label: const Text('Add infrastructure'),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemBuilder: (_, index) => _InfrastructureCard(item: items[index]),
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemCount: items.length,
          );
        },
      ),
    );
  }

  Future<void> _showCreateSheet(BuildContext context, WidgetRef ref) async {
    final credential = await showModalBottomSheet<RegistrationCredential?>(
      context: context,
      isScrollControlled: true,
      builder: (context) => const _CreateInfrastructureSheet(),
    );
    if (credential != null && context.mounted) {
      await _showRegistrationCommand(context, credential);
    }
  }

  Future<void> _showRegistrationCommand(
    BuildContext context,
    RegistrationCredential credential,
  ) async {
    await showDialog<void>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('Waiting For Agent'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Credential expires ${credential.expiresAt.toLocal()}'),
                const SizedBox(height: 12),
                SelectableText(
                  credential.installCommand,
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(fontFamily: 'monospace'),
                ),
              ],
            ),
            actions: [
              TextButton.icon(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.check),
                label: const Text('Done'),
              ),
            ],
          ),
    );
  }
}

class _InfrastructureCard extends StatelessWidget {
  const _InfrastructureCard({required this.item});

  final InfrastructureSummary item;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(
              item.type == InfrastructureType.selfHosted
                  ? Icons.dns
                  : Icons.cloud,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.name,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text('${item.healthStatus} - ${item.type.wireName}'),
                ],
              ),
            ),
            Chip(label: Text('${item.capabilities.length} caps')),
          ],
        ),
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

class _SkeletonList extends StatelessWidget {
  const _SkeletonList();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemBuilder:
          (_, __) =>
              const Card(child: SizedBox(height: 84, width: double.infinity)),
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemCount: 4,
    );
  }
}

class _CreateInfrastructureSheet extends ConsumerStatefulWidget {
  const _CreateInfrastructureSheet();

  @override
  ConsumerState<_CreateInfrastructureSheet> createState() => _CreateInfrastructureSheetState();
}

class _CreateInfrastructureSheetState extends ConsumerState<_CreateInfrastructureSheet> {
  final nameController = TextEditingController();
  InfrastructureType type = InfrastructureType.selfHosted;

  @override
  void dispose() {
    nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        16 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Add Infrastructure',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: nameController,
              decoration: const InputDecoration(labelText: 'Name'),
              autofocus: true,
            ),
            const SizedBox(height: 12),
            SegmentedButton<InfrastructureType>(
              segments: const [
                ButtonSegment(
                  value: InfrastructureType.selfHosted,
                  icon: Icon(Icons.dns),
                  label: Text('Self-hosted'),
                ),
                ButtonSegment(
                  value: InfrastructureType.managed,
                  icon: Icon(Icons.cloud),
                  label: Text('Managed'),
                ),
              ],
              selected: {type},
              onSelectionChanged: (selection) => setState(() => type = selection.first),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () async {
                final name = nameController.text.trim();
                if (name.isEmpty) {
                  return;
                }
                final repository = ref.read(infrastructureRepositoryProvider);
                final created = await repository.create(name: name, type: type);
                RegistrationCredential? credential;
                if (type == InfrastructureType.selfHosted) {
                  credential = await repository.createRegistrationCredential(created.id);
                }
                ref.invalidate(infrastructureListProvider);
                if (context.mounted) {
                  Navigator.of(context).pop(credential);
                }
              },
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }
}
