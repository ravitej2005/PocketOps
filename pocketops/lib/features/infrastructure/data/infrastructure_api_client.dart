import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pocketops/core/config/app_config.dart';

class InfrastructureApiClient {
  InfrastructureApiClient({http.Client? httpClient})
    : _httpClient = httpClient ?? http.Client();

  final http.Client _httpClient;

  Future<List<InfrastructureSummary>> list(String accessToken) async {
    final response = await _httpClient.get(
      Uri.parse('${AppConfig.apiBaseUrl}/api/infrastructures'),
      headers: {'Authorization': 'Bearer $accessToken'},
    );
    if (response.statusCode != 200) {
      throw InfrastructureApiException(response.statusCode);
    }
    final body = jsonDecode(response.body) as List<dynamic>;
    return body
        .cast<Map<String, dynamic>>()
        .map(InfrastructureSummary.fromJson)
        .toList();
  }

  Future<InfrastructureSummary> create({
    required String accessToken,
    required String name,
    required InfrastructureType type,
    String? providerType,
  }) async {
    final response = await _httpClient.post(
      Uri.parse('${AppConfig.apiBaseUrl}/api/infrastructures'),
      headers: {
        'Authorization': 'Bearer $accessToken',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'name': name,
        'type': type.wireName,
        if (providerType != null) 'providerType': providerType,
      }),
    );
    if (response.statusCode != 200) {
      throw InfrastructureApiException(response.statusCode);
    }
    return InfrastructureSummary.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  Future<void> delete({required String accessToken, required String id}) async {
    final response = await _httpClient.delete(
      Uri.parse('${AppConfig.apiBaseUrl}/api/infrastructures/$id'),
      headers: {'Authorization': 'Bearer $accessToken'},
    );
    if (response.statusCode != 204) {
      throw InfrastructureApiException(response.statusCode);
    }
  }

  Future<RegistrationCredential> createRegistrationCredential({
    required String accessToken,
    required String infrastructureId,
  }) async {
    final response = await _httpClient.post(
      Uri.parse(
        '${AppConfig.apiBaseUrl}/api/infrastructures/$infrastructureId/agent-registration',
      ),
      headers: {'Authorization': 'Bearer $accessToken'},
    );
    if (response.statusCode != 200) {
      throw InfrastructureApiException(response.statusCode);
    }
    return RegistrationCredential.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }
}

class InfrastructureSummary {
  const InfrastructureSummary({
    required this.id,
    required this.name,
    required this.type,
    required this.healthStatus,
    required this.capabilities,
  });

  final String id;
  final String name;
  final InfrastructureType type;
  final String healthStatus;
  final List<String> capabilities;

  factory InfrastructureSummary.fromJson(Map<String, dynamic> json) {
    return InfrastructureSummary(
      id: json['id'] as String,
      name: json['name'] as String,
      type: InfrastructureTypeWire.fromWireName(json['type'] as String),
      healthStatus: json['healthStatus'] as String,
      capabilities:
          (json['capabilities'] as List<dynamic>).cast<String>().toList(),
    );
  }
}

class RegistrationCredential {
  const RegistrationCredential({
    required this.registrationToken,
    required this.expiresAt,
    required this.installCommand,
  });

  final String registrationToken;
  final DateTime expiresAt;
  final String installCommand;

  factory RegistrationCredential.fromJson(Map<String, dynamic> json) {
    return RegistrationCredential(
      registrationToken: json['registrationToken'] as String,
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      installCommand: json['installCommand'] as String,
    );
  }
}

enum InfrastructureType { selfHosted, managed }

extension InfrastructureTypeWire on InfrastructureType {
  String get wireName {
    return switch (this) {
      InfrastructureType.selfHosted => 'SELF_HOSTED',
      InfrastructureType.managed => 'MANAGED',
    };
  }

  static InfrastructureType fromWireName(String value) {
    return switch (value) {
      'SELF_HOSTED' => InfrastructureType.selfHosted,
      'MANAGED' => InfrastructureType.managed,
      _ => throw FormatException('Unknown infrastructure type $value.'),
    };
  }
}

class InfrastructureApiException implements Exception {
  const InfrastructureApiException(this.statusCode);

  final int statusCode;

  @override
  String toString() => 'Infrastructure request failed with status $statusCode.';
}
