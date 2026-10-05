import 'dart:convert';

import 'package:pocketops/features/auth/data/auth_api_client.dart';
import 'package:pocketops/features/auth/data/auth_token_store.dart';
import 'package:pocketops/core/config/app_config.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';

class InfrastructureRepository {
  InfrastructureRepository({
    required InfrastructureApiClient apiClient,
    required AuthApiClient authApiClient,
    required AuthTokenStore tokenStore,
  }) : _apiClient = apiClient,
       _authApiClient = authApiClient,
       _tokenStore = tokenStore;

  final InfrastructureApiClient _apiClient;
  final AuthApiClient _authApiClient;
  final AuthTokenStore _tokenStore;
  Future<String>? _refreshingAccessToken;

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

  Future<Uri> updatesStreamUri(String infrastructureId) {
    return metricStreamUri(infrastructureId);
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

  Future<ResourceActionResponse> executeInfrastructureAction({
    required String infrastructureId,
    required String action,
  }) async {
    return _apiClient.executeInfrastructureAction(
      accessToken: await _accessToken(),
      infrastructureId: infrastructureId,
      action: action,
    );
  }

  Future<ResourceActionResponse> executeAction({
    required String infrastructureId,
    required String resourceId,
    required String action,
  }) async {
    return _apiClient.executeAction(
      accessToken: await _accessToken(),
      infrastructureId: infrastructureId,
      resourceId: resourceId,
      action: action,
    );
  }

  Future<String> _accessToken() async {
    final tokens = await _tokenStore.read();
    if (tokens == null) {
      throw StateError('Not signed in.');
    }
    if (!_isExpiredOrNearExpiry(tokens.accessToken)) {
      return tokens.accessToken;
    }

    final refresh =
        _refreshingAccessToken ??= _refreshAccessToken(tokens.refreshToken);
    try {
      return await refresh;
    } finally {
      if (identical(_refreshingAccessToken, refresh)) {
        _refreshingAccessToken = null;
      }
    }
  }

  Future<String> _refreshAccessToken(String refreshToken) async {
    final session = await _authApiClient.refresh(refreshToken);
    await _tokenStore.save(
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
    );
    return session.accessToken;
  }

  bool _isExpiredOrNearExpiry(String accessToken) {
    final parts = accessToken.split('.');
    if (parts.length != 3) {
      return true;
    }
    try {
      final payload =
          jsonDecode(
                utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
              )
              as Map<String, dynamic>;
      final expiry = payload['exp'];
      if (expiry is! num) {
        return true;
      }
      return DateTime.now()
          .add(const Duration(seconds: 30))
          .isAfter(DateTime.fromMillisecondsSinceEpoch(expiry.toInt() * 1000));
    } on FormatException {
      return true;
    }
  }
}
