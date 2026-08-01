import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/core/routing/app_router.dart';
import 'package:pocketops/core/theme/app_theme.dart';

void main() {
  runApp(const ProviderScope(child: PocketOpsApp()));
}

class PocketOpsApp extends StatelessWidget {
  const PocketOpsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PocketOps',
      theme: AppTheme.lightTheme,
      onGenerateRoute: AppRouter.onGenerateRoute,
      initialRoute: AppRouter.authRoute,
    );
  }
}
