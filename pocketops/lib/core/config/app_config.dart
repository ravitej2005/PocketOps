class AppConfig {
  static const String apiBaseUrl = String.fromEnvironment(
    'POCKETOPS_API_BASE_URL',
    defaultValue: 'http://13.206.16.241:8080',
  );
}
