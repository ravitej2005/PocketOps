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

  Future<List<InfrastructureResource>> resources({
    required String accessToken,
    required String infrastructureId,
  }) async {
    final response = await _httpClient.get(
      Uri.parse(
        '${AppConfig.apiBaseUrl}/api/infrastructures/$infrastructureId/resources',
      ),
      headers: {'Authorization': 'Bearer $accessToken'},
    );
    if (response.statusCode != 200) {
      throw InfrastructureApiException(response.statusCode);
    }
    final body = jsonDecode(response.body) as List<dynamic>;
    return body
        .cast<Map<String, dynamic>>()
        .map(InfrastructureResource.fromJson)
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

class InfrastructureResource {
  const InfrastructureResource({
    required this.id,
    required this.externalResourceId,
    required this.displayName,
    required this.resourceType,
    required this.status,
    required this.criticality,
    required this.lastSeenAt,
  });

  final String id;
  final String externalResourceId;
  final String displayName;
  final String resourceType;
  final String status;
  final String criticality;
  final DateTime? lastSeenAt;

  factory InfrastructureResource.fromJson(Map<String, dynamic> json) {
    return InfrastructureResource(
      id: json['id'] as String,
      externalResourceId: json['externalResourceId'] as String,
      displayName: json['displayName'] as String,
      resourceType: json['resourceType'] as String,
      status: json['status'] as String,
      criticality: json['criticality'] as String,
      lastSeenAt:
          json['lastSeenAt'] == null
              ? null
              : DateTime.parse(json['lastSeenAt'] as String),
    );
  }
}

class ContainerMetricUpdate {
  const ContainerMetricUpdate({
    required this.infrastructureId,
    required this.resourceId,
    required this.cpuPercent,
    required this.memoryUsageBytes,
    required this.memoryLimitBytes,
    required this.networkRxBytes,
    required this.networkTxBytes,
    required this.uptimeSeconds,
    required this.timestamp,
  });

  final String infrastructureId;
  final String resourceId;
  final double cpuPercent;
  final int memoryUsageBytes;
  final int memoryLimitBytes;
  final int networkRxBytes;
  final int networkTxBytes;
  final int uptimeSeconds;
  final DateTime timestamp;

  factory ContainerMetricUpdate.fromJson(Map<String, dynamic> json) {
    return ContainerMetricUpdate(
      infrastructureId: json['infrastructureId'] as String,
      resourceId: json['resourceId'] as String,
      cpuPercent: (json['cpuPercent'] as num).toDouble(),
      memoryUsageBytes: json['memoryUsageBytes'] as int,
      memoryLimitBytes: json['memoryLimitBytes'] as int,
      networkRxBytes: json['networkRxBytes'] as int,
      networkTxBytes: json['networkTxBytes'] as int,
      uptimeSeconds: json['uptimeSeconds'] as int,
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        json['timestampUnixMs'] as int,
      ),
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
