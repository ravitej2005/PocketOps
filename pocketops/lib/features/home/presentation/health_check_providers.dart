import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/core/network/health_api_client.dart';

final healthApiClientProvider = Provider<HealthApiClient>((ref) {
  return HealthApiClient();
});

final healthSnapshotProvider = FutureProvider.autoDispose<HealthSnapshot>((
  ref,
) {
  final client = ref.watch(healthApiClientProvider);
  return client.fetchHealth();
});
