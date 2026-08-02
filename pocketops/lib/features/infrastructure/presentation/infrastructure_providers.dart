import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/features/auth/presentation/auth_controller.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_repository.dart';

final infrastructureApiClientProvider = Provider<InfrastructureApiClient>((
  ref,
) {
  return InfrastructureApiClient();
});

final infrastructureRepositoryProvider = Provider<InfrastructureRepository>((
  ref,
) {
  return InfrastructureRepository(
    apiClient: ref.watch(infrastructureApiClientProvider),
    tokenStore: ref.watch(authTokenStoreProvider),
  );
});

final infrastructureListProvider =
    FutureProvider.autoDispose<List<InfrastructureSummary>>((ref) {
      return ref.watch(infrastructureRepositoryProvider).list();
    });

final infrastructureResourcesProvider = FutureProvider.autoDispose
    .family<List<InfrastructureResource>, String>((ref, infrastructureId) {
      return ref
          .watch(infrastructureRepositoryProvider)
          .resources(infrastructureId);
    });
