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
      var disposed = false;

      void closeAllSockets() {
        for (final socket in sockets) {
          socket.close();
        }
        sockets.clear();
      }

      Future<void>(() async {
        final repository = ref.read(infrastructureRepositoryProvider);
        final items = await repository.list();
        if (disposed) return;
        // Mutable working copy — updated in-place by WebSocket events.
        var current = List<InfrastructureSummary>.from(items);
        controller.add(current);

        for (final item in current) {
          if (disposed) return;
          final uri = await repository.updatesStreamUri(item.id);
          if (disposed) return;
          final socket = await WebSocket.connect(uri.toString());
          if (disposed) {
            await socket.close();
            return;
          }
          sockets.add(socket);
          socket.listen((message) {
            if (disposed) return;
            final json = jsonDecode(message as String) as Map<String, dynamic>;
            if (json['type'] != 'InfrastructureStateChanged') return;
            final update = InfrastructureStateUpdate.fromJson(json);
            current = current
                .map(
                  (item) =>
                      item.id == update.infrastructureId
                          ? item.copyWith(healthStatus: update.healthStatus)
                          : item,
                )
                .toList();
            if (!disposed) controller.add(current);
          }, onDone: () {
            // Socket closed; ignore — periodic snapshot will reconnect.
          });
        }
      }).catchError(controller.addError);

      ref.onDispose(() {
        disposed = true;
        closeAllSockets();
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
