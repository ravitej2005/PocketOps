import 'dart:async';
import 'dart:convert';
import 'dart:io';

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

final liveInfrastructureListProvider =
    StreamProvider.autoDispose<List<InfrastructureSummary>>((ref) {
      final controller = StreamController<List<InfrastructureSummary>>();
      final sockets = <WebSocket>[];
      var items = <InfrastructureSummary>[];
      var disposed = false;

      Future<void>(() async {
        final repository = ref.read(infrastructureRepositoryProvider);
        items = await repository.list();
        if (disposed) {
          return;
        }
        controller.add(items);
        for (final item in items) {
          final uri = await repository.updatesStreamUri(item.id);
          if (disposed) {
            return;
          }
          final socket = await WebSocket.connect(uri.toString());
          sockets.add(socket);
          socket.listen((message) {
            final json = jsonDecode(message as String) as Map<String, dynamic>;
            if (json['type'] != 'InfrastructureStateChanged') {
              return;
            }
            final update = InfrastructureStateUpdate.fromJson(json);
            items =
                items
                    .map(
                      (item) =>
                          item.id == update.infrastructureId
                              ? item.copyWith(healthStatus: update.healthStatus)
                              : item,
                    )
                    .toList();
            controller.add(items);
          });
        }
      }).catchError(controller.addError);

      ref.onDispose(() {
        disposed = true;
        for (final socket in sockets) {
          socket.close();
        }
        controller.close();
      });
      return controller.stream;
    });

final infrastructureResourcesProvider = FutureProvider.autoDispose
    .family<List<InfrastructureResource>, String>((ref, infrastructureId) {
      return ref
          .watch(infrastructureRepositoryProvider)
          .resources(infrastructureId);
    });
