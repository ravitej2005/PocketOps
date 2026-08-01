import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pocketops/core/config/app_config.dart';

class HealthApiClient {
  HealthApiClient({http.Client? httpClient})
    : _httpClient = httpClient ?? http.Client();

  final http.Client _httpClient;

  Future<HealthSnapshot> fetchHealth() async {
    final uri = Uri.parse('${AppConfig.apiBaseUrl}/api/health');
    final response = await _httpClient.get(uri);

    if (response.statusCode != 200) {
      throw HealthCheckException(
        'Health check failed with status ${response.statusCode}.',
      );
    }

    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return HealthSnapshot(
      status: body['status'] as String? ?? 'UNKNOWN',
      database: body['database'] as String? ?? 'UNKNOWN',
      requestId: response.headers['x-request-id'],
    );
  }
}

class HealthSnapshot {
  const HealthSnapshot({
    required this.status,
    required this.database,
    this.requestId,
  });

  final String status;
  final String database;
  final String? requestId;
}

class HealthCheckException implements Exception {
  const HealthCheckException(this.message);

  final String message;

  @override
  String toString() => message;
}
