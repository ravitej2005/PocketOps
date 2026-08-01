import 'package:flutter/material.dart';
import 'package:pocketops/features/auth/presentation/auth_screen.dart';
import 'package:pocketops/features/home/presentation/health_check_screen.dart';
import 'package:pocketops/features/infrastructure/presentation/infrastructure_list_screen.dart';

class AppRouter {
  static const String authRoute = '/';
  static const String healthRoute = '/health';
  static const String infrastructuresRoute = '/infrastructures';

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case authRoute:
        return MaterialPageRoute<void>(
          builder: (_) => const AuthScreen(),
          settings: settings,
        );
      case healthRoute:
        return MaterialPageRoute<void>(
          builder: (_) => const HealthCheckScreen(),
          settings: settings,
        );
      case infrastructuresRoute:
        return MaterialPageRoute<void>(
          builder: (_) => const InfrastructureListScreen(),
          settings: settings,
        );
      default:
        return MaterialPageRoute<void>(
          builder: (_) => const HealthCheckScreen(),
          settings: settings,
        );
    }
  }

  AppRouter._();
}
