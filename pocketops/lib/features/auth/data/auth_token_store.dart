import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract class AuthTokenStore {
  Future<StoredAuthTokens?> read();

  Future<void> save({
    required String accessToken,
    required String refreshToken,
  });

  Future<void> clear();
}

class SecureAuthTokenStore implements AuthTokenStore {
  SecureAuthTokenStore({FlutterSecureStorage? secureStorage})
    : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'pocketops.auth.access_token';
  static const _refreshTokenKey = 'pocketops.auth.refresh_token';

  final FlutterSecureStorage _secureStorage;

  @override
  Future<StoredAuthTokens?> read() async {
    final accessToken = await _secureStorage.read(key: _accessTokenKey);
    final refreshToken = await _secureStorage.read(key: _refreshTokenKey);
    if (accessToken == null || refreshToken == null) {
      return null;
    }
    return StoredAuthTokens(
      accessToken: accessToken,
      refreshToken: refreshToken,
    );
  }

  @override
  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _secureStorage.write(key: _accessTokenKey, value: accessToken);
    await _secureStorage.write(key: _refreshTokenKey, value: refreshToken);
  }

  @override
  Future<void> clear() async {
    await _secureStorage.delete(key: _accessTokenKey);
    await _secureStorage.delete(key: _refreshTokenKey);
  }
}

class MemoryAuthTokenStore implements AuthTokenStore {
  StoredAuthTokens? _tokens;

  @override
  Future<StoredAuthTokens?> read() async => _tokens;

  @override
  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    _tokens = StoredAuthTokens(
      accessToken: accessToken,
      refreshToken: refreshToken,
    );
  }

  @override
  Future<void> clear() async {
    _tokens = null;
  }
}

class StoredAuthTokens {
  const StoredAuthTokens({
    required this.accessToken,
    required this.refreshToken,
  });

  final String accessToken;
  final String refreshToken;
}
