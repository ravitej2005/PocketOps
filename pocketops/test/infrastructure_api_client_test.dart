import 'package:flutter_test/flutter_test.dart';
import 'package:pocketops/features/infrastructure/data/infrastructure_api_client.dart';

void main() {
  test('infrastructure summary parses typed response', () {
    final summary = InfrastructureSummary.fromJson(const {
      'id': 'infra-1',
      'name': 'StormAPI',
      'type': 'SELF_HOSTED',
      'healthStatus': 'UNKNOWN',
      'capabilities': ['METRICS', 'LOGS'],
    });

    expect(summary.id, 'infra-1');
    expect(summary.type, InfrastructureType.selfHosted);
    expect(summary.capabilities, contains('METRICS'));
  });

  test('registration credential parses installer command', () {
    final credential = RegistrationCredential.fromJson(const {
      'registrationToken': 'token',
      'expiresAt': '2026-07-31T02:30:00Z',
      'installCommand': 'pocketops-agent --token token',
    });

    expect(credential.registrationToken, 'token');
    expect(credential.expiresAt.toUtc().year, 2026);
    expect(credential.installCommand, contains('pocketops-agent'));
  });
}
