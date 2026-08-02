class AppConfig {
  static const String apiBaseUrl = String.fromEnvironment(
    'POCKETOPS_API_BASE_URL',
    defaultValue: 'http://13.206.16.241:8080',
  );

  static String get wsBaseUrl {
    final uri = Uri.parse(apiBaseUrl);
    return uri.replace(scheme: uri.scheme == 'https' ? 'wss' : 'ws').toString();
  }
}
