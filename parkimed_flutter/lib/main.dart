import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'core/theme/app_theme.dart';
import 'cubits/assessment/assessment_cubit.dart';
import 'cubits/history/history_cubit.dart';
import 'cubits/medication/medication_cubit.dart';
import 'screens/splash/splash_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Configure status bar style for clean light theme
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  runApp(const ParkiMedApp());
}

class ParkiMedApp extends StatelessWidget {
  const ParkiMedApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<AssessmentCubit>(create: (_) => AssessmentCubit()),
        BlocProvider<HistoryCubit>(create: (_) => HistoryCubit()),
        BlocProvider<MedicationCubit>(create: (_) => MedicationCubit()),
      ],
      child: MaterialApp(
        title: 'parkiMed',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        home: const SplashScreen(),
      ),
    );
  }
}
