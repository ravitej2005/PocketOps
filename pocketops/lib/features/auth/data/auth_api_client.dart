import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pocketops/core/config/app_config.dart';

class AuthApiClient {
  AuthApiClient({http.Client? httpClient})
    : _httpClient = httpClient ?? http.Client();

  final http.Client _httpClient;

  Future<AuthSession> register({
    required String email,
    required String password,
    required String deviceName,
    required String platform,
  }) {
    return _postSession('/api/auth/register', {
      'email': email,
      'password': password,
      'deviceName': deviceName,
      'platform': platform,
    });
  }

  Future<AuthSession> login({
    required String email,
    required String password,
    required String deviceName,
    required String platform,
  }) {
    return _postSession('/api/auth/login', {
      'email': email,
      'password': password,
      'deviceName': deviceName,
      'platform': platform,
    });
  }

  Future<AuthSession> loginWithGitHubCode({
    required String code,
    required String redirectUri,
    required String deviceName,
    required String platform,
  }) {
    return _postSession('/api/auth/github', {
      'code': code,
      'redirectUri': redirectUri,
      'deviceName': deviceName,
      'platform': platform,
    });
  }

  Future<AuthSession> refresh(String refreshToken) {
    return _postSession('/api/auth/refresh', {'refreshToken': refreshToken});
  }

  Future<void> logout(String accessToken) async {
    await _postEmpty('/api/auth/logout', accessToken);
  }

  Future<void> logoutAll(String accessToken) async {
    await _postEmpty('/api/auth/logout-all', accessToken);
  }

  Future<AuthSession> _postSession(
    String path,
    Map<String, String> body,
  ) async {
    final response = await _httpClient.post(
      Uri.parse('${AppConfig.apiBaseUrl}$path'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    final decoded = _decode(response);
    if (response.statusCode != 200) {
      throw AuthApiException.fromBody(decoded, response.statusCode);
    }
    return AuthSession.fromJson(decoded);
  }

  Future<void> _postEmpty(String path, String accessToken) async {
    final response = await _httpClient.post(
      Uri.parse('${AppConfig.apiBaseUrl}$path'),
      headers: {'Authorization': 'Bearer $accessToken'},
    );
    if (response.statusCode != 200) {
      throw AuthApiException.fromBody(_decode(response), response.statusCode);
    }
  }

  Map<String, dynamic> _decode(http.Response response) {
    if (response.body.isEmpty) {
      return const {};
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }
}

class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final AuthUser user;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

class AuthUser {
  const AuthUser({required this.id, required this.email});

  final String id;
  final String email;

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(id: json['id'] as String, email: json['email'] as String);
  }
}

class AuthApiException implements Exception {
  const AuthApiException({
    required this.code,
    required this.message,
    required this.statusCode,
  });

  final String code;
  final String message;
  final int statusCode;

  factory AuthApiException.fromBody(Map<String, dynamic> body, int statusCode) {
    return AuthApiException(
      code: body['code'] as String? ?? 'UNKNOWN',
      message: body['message'] as String? ?? 'Authentication failed.',
      statusCode: statusCode,
    );
  }

  @override
  String toString() => message;
}
