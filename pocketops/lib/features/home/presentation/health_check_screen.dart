import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/core/routing/app_router.dart';
import 'package:pocketops/features/home/presentation/health_check_providers.dart';

class HealthCheckScreen extends ConsumerWidget {
  const HealthCheckScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final healthAsync = ref.watch(healthSnapshotProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('PocketOps')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: healthAsync.when(
          loading: () => const _LoadingState(),
          error:
              (error, _) => _ErrorState(
                message: '$error',
                onRetry: () => ref.invalidate(healthSnapshotProvider),
              ),
          data:
              (snapshot) => _DataState(
                status: snapshot.status,
                database: snapshot.database,
                requestId: snapshot.requestId ?? 'n/a',
                onRefresh: () => ref.invalidate(healthSnapshotProvider),
              ),
        ),
      ),
    );
  }
}

class _LoadingState extends StatelessWidget {
  const _LoadingState();

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator());
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.wifi_off, size: 48),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}

class _DataState extends StatelessWidget {
  const _DataState({
    required this.status,
    required this.database,
    required this.requestId,
    required this.onRefresh,
  });

  final String status;
  final String database;
  final String requestId;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 360),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Backend Health',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 12),
                Text('Status: $status'),
                Text('Database: $database'),
                const SizedBox(height: 8),
                Text(
                  'Request ID: $requestId',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                Align(
                  alignment: Alignment.centerRight,
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      FilledButton.tonalIcon(
                        onPressed: onRefresh,
                        icon: const Icon(Icons.refresh),
                        label: const Text('Refresh'),
                      ),
                      FilledButton.icon(
                        onPressed:
                            () => Navigator.of(
                              context,
                            ).pushNamed(AppRouter.infrastructuresRoute),
                        icon: const Icon(Icons.dns),
                        label: const Text('Infrastructures'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
