import 'package:flutter_test/flutter_test.dart';
import 'package:parkimed_flutter/main.dart';

void main() {
  testWidgets('ParkiMed smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const ParkiMedApp());

    // Verify SplashScreen displays parkiMed
    expect(find.text('parkiMed'), findsOneWidget);

    // Pump past the splash delay to MainScreen
    await tester.pumpAndSettle(const Duration(milliseconds: 2500));

    // Verify Dashboard displays parkiMed
    expect(find.text('parkiMed'), findsOneWidget);
  });
}
