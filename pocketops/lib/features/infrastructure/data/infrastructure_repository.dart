import 'package:pocketops/features/auth/data/auth_token_store.dart';
import 'package:pocketops/core/config/app_config.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';

class InfrastructureRepository {
  InfrastructureRepository({
    required InfrastructureApiClient apiClient,
    required AuthTokenStore tokenStore,
  }) : _apiClient = apiClient,
       _tokenStore = tokenStore;

  final InfrastructureApiClient _apiClient;
  final AuthTokenStore _tokenStore;

  Future<List<InfrastructureSummary>> list() async {
    return _apiClient.list(await _accessToken());
  }

  Future<List<InfrastructureResource>> resources(
    String infrastructureId,
  ) async {
    return _apiClient.resources(
      accessToken: await _accessToken(),
      infrastructureId: infrastructureId,
    );
  }

  Future<Uri> metricStreamUri(String infrastructureId) async {
    return Uri.parse(
      '${AppConfig.wsBaseUrl}/ws/infrastructures/$infrastructureId',
    ).replace(queryParameters: {'token': await _accessToken()});
  }

  Future<InfrastructureSummary> create({
    required String name,
    required InfrastructureType type,
    String? providerType,
  }) async {
    return _apiClient.create(
      accessToken: await _accessToken(),
      name: name,
      type: type,
      providerType: providerType,
    );
  }

  Future<void> delete(String id) async {
    await _apiClient.delete(accessToken: await _accessToken(), id: id);
  }

  Future<RegistrationCredential> createRegistrationCredential(
    String infrastructureId,
  ) async {
    return _apiClient.createRegistrationCredential(
      accessToken: await _accessToken(),
      infrastructureId: infrastructureId,
    );
  }

  Future<String> _accessToken() async {
    final tokens = await _tokenStore.read();
    if (tokens == null) {
      throw StateError('Not signed in.');
    }
    return tokens.accessToken;
  }
}
