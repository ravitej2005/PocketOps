import 'package:flutter_test/flutter_test.dart';
import 'package:pocketops/features/auth/data/auth_token_store.dart';

void main() {
  test('memory token store saves reads and clears auth tokens', () async {
    final store = MemoryAuthTokenStore();

    expect(await store.read(), isNull);

    await store.save(accessToken: 'access', refreshToken: 'refresh');
    final tokens = await store.read();

    expect(tokens?.accessToken, 'access');
    expect(tokens?.refreshToken, 'refresh');

    await store.clear();
    expect(await store.read(), isNull);
  });
}
