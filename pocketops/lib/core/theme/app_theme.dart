import 'package:flutter/material.dart';

class AppTheme {
  static final ThemeData lightTheme = ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF2B6CB0),
      brightness: Brightness.light,
    ),
    scaffoldBackgroundColor: const Color(0xFFF7FAFC),
    useMaterial3: true,
  );

  AppTheme._();
}
