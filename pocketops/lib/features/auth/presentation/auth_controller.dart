import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/features/auth/data/auth_api_client.dart';
import 'package:pocketops/features/auth/data/auth_repository.dart';
import 'package:pocketops/features/auth/data/auth_token_store.dart';

final authApiClientProvider = Provider<AuthApiClient>((ref) {
  return AuthApiClient();
});

final authTokenStoreProvider = Provider<AuthTokenStore>((ref) {
  return SecureAuthTokenStore();
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    apiClient: ref.watch(authApiClientProvider),
    tokenStore: ref.watch(authTokenStoreProvider),
  );
});

final authControllerProvider = StateNotifierProvider<AuthController, AuthState>(
  (ref) {
    return AuthController(ref.watch(authRepositoryProvider))..restoreSession();
  },
);

class AuthController extends StateNotifier<AuthState> {
  AuthController(this._repository) : super(const AuthState.unknown());

  final AuthRepository _repository;

  Future<void> restoreSession() async {
    state = const AuthState.loading();
    try {
      final session = await _repository.restoreSession();
      state =
          session == null
              ? const AuthState.signedOut()
              : AuthState.signedIn(session.user.email);
    } catch (_) {
      state = const AuthState.signedOut();
    }
  }

  Future<void> register({
    required String email,
    required String password,
    required String deviceName,
  }) async {
    await _authenticate(
      () => _repository.register(
        email: email,
        password: password,
        deviceName: deviceName,
      ),
    );
  }

  Future<void> login({
    required String email,
    required String password,
    required String deviceName,
  }) async {
    await _authenticate(
      () => _repository.login(
        email: email,
        password: password,
        deviceName: deviceName,
      ),
    );
  }

  Future<void> loginWithGitHubCode({
    required String code,
    required String deviceName,
  }) async {
    await _authenticate(
      () => _repository.loginWithGitHubCode(code: code, deviceName: deviceName),
    );
  }

  Future<void> logout() async {
    state = const AuthState.loading();
    await _repository.logout();
    state = const AuthState.signedOut();
  }

  Future<void> _authenticate(Future<AuthSession> Function() action) async {
    state = const AuthState.loading();
    try {
      final session = await action();
      state = AuthState.signedIn(session.user.email);
    } catch (error) {
      state = AuthState.error('$error');
    }
  }
}

class AuthState {
  const AuthState._({required this.status, this.email, this.message});

  const AuthState.unknown() : this._(status: AuthStatus.unknown);

  const AuthState.loading() : this._(status: AuthStatus.loading);

  const AuthState.signedOut() : this._(status: AuthStatus.signedOut);

  const AuthState.signedIn(String email)
    : this._(status: AuthStatus.signedIn, email: email);

  const AuthState.error(String message)
    : this._(status: AuthStatus.error, message: message);

  final AuthStatus status;
  final String? email;
  final String? message;
}

enum AuthStatus { unknown, loading, signedOut, signedIn, error }
