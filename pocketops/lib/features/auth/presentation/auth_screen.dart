import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pocketops/core/routing/app_router.dart';
import 'package:pocketops/features/auth/presentation/auth_controller.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key});

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _deviceController = TextEditingController(text: 'Android device');
  final _githubCodeController = TextEditingController();
  bool _registerMode = false;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _deviceController.dispose();
    _githubCodeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authControllerProvider);
    final isBusy =
        authState.status == AuthStatus.loading ||
        authState.status == AuthStatus.unknown;

    ref.listen(authControllerProvider, (_, next) {
      if (next.status == AuthStatus.signedIn) {
        Navigator.of(context).pushReplacementNamed(AppRouter.healthRoute);
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text('PocketOps')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    _registerMode ? 'Create account' : 'Sign in',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 20),
                  Form(
                    key: _formKey,
                    child: Column(
                      children: [
                        TextFormField(
                          controller: _emailController,
                          decoration: const InputDecoration(labelText: 'Email'),
                          keyboardType: TextInputType.emailAddress,
                          validator: _required,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _passwordController,
                          decoration: const InputDecoration(
                            labelText: 'Password',
                          ),
                          obscureText: true,
                          validator: _required,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _deviceController,
                          decoration: const InputDecoration(
                            labelText: 'Device name',
                          ),
                          validator: _required,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: isBusy ? null : _submitPassword,
                    child: Text(_registerMode ? 'Create account' : 'Sign in'),
                  ),
                  TextButton(
                    onPressed:
                        isBusy
                            ? null
                            : () =>
                                setState(() => _registerMode = !_registerMode),
                    child: Text(
                      _registerMode ? 'Use existing account' : 'Create account',
                    ),
                  ),
                  const Divider(height: 32),
                  TextField(
                    controller: _githubCodeController,
                    decoration: const InputDecoration(labelText: 'GitHub code'),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: isBusy ? null : _submitGitHub,
                    child: const Text('Continue with GitHub'),
                  ),
                  if (authState.status == AuthStatus.error) ...[
                    const SizedBox(height: 16),
                    Text(
                      authState.message ?? 'Authentication failed.',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  String? _required(String? value) {
    return value == null || value.trim().isEmpty ? 'Required' : null;
  }

  Future<void> _submitPassword() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final controller = ref.read(authControllerProvider.notifier);
    if (_registerMode) {
      await controller.register(
        email: _emailController.text.trim(),
        password: _passwordController.text,
        deviceName: _deviceController.text.trim(),
      );
    } else {
      await controller.login(
        email: _emailController.text.trim(),
        password: _passwordController.text,
        deviceName: _deviceController.text.trim(),
      );
    }
  }

  Future<void> _submitGitHub() async {
    final code = _githubCodeController.text.trim();
    if (code.isEmpty) {
      return;
    }
    await ref
        .read(authControllerProvider.notifier)
        .loginWithGitHubCode(
          code: code,
          deviceName:
              _deviceController.text.trim().isEmpty
                  ? 'Android device'
                  : _deviceController.text.trim(),
        );
  }
}
