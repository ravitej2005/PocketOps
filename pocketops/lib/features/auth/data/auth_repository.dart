import 'package:pocketops/features/auth/data/auth_api_client.dart';
import 'package:pocketops/features/auth/data/auth_token_store.dart';

class AuthRepository {
  AuthRepository({
    required AuthApiClient apiClient,
    required AuthTokenStore tokenStore,
  }) : _apiClient = apiClient,
       _tokenStore = tokenStore;

  final AuthApiClient _apiClient;
  final AuthTokenStore _tokenStore;

  Future<AuthSession?> restoreSession() async {
    final tokens = await _tokenStore.read();
    if (tokens == null) {
      return null;
    }
    try {
      final session = await _apiClient.refresh(tokens.refreshToken);
      await _tokenStore.save(
        accessToken: session.accessToken,
        refreshToken: session.refreshToken,
      );
      return session;
    } catch (_) {
      await _tokenStore.clear();
      rethrow;
    }
  }

  Future<AuthSession> register({
    required String email,
    required String password,
    required String deviceName,
  }) async {
    final session = await _apiClient.register(
      email: email,
      password: password,
      deviceName: deviceName,
      platform: 'android',
    );
    await _save(session);
    return session;
  }

  Future<AuthSession> login({
    required String email,
    required String password,
    required String deviceName,
  }) async {
    final session = await _apiClient.login(
      email: email,
      password: password,
      deviceName: deviceName,
      platform: 'android',
    );
    await _save(session);
    return session;
  }

  Future<AuthSession> loginWithGitHubCode({
    required String code,
    required String deviceName,
  }) async {
    final session = await _apiClient.loginWithGitHubCode(
      code: code,
      redirectUri: 'pocketops://oauth/github',
      deviceName: deviceName,
      platform: 'android',
    );
    await _save(session);
    return session;
  }

  Future<void> logout() async {
    final tokens = await _tokenStore.read();
    if (tokens != null) {
      await _apiClient.logout(tokens.accessToken);
    }
    await _tokenStore.clear();
  }

  Future<void> logoutAll() async {
    final tokens = await _tokenStore.read();
    if (tokens != null) {
      await _apiClient.logoutAll(tokens.accessToken);
    }
    await _tokenStore.clear();
  }

  Future<void> _save(AuthSession session) {
    return _tokenStore.save(
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
    );
  }
}
