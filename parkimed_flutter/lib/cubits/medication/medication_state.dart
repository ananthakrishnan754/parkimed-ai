import 'package:equatable/equatable.dart';
import '../../core/models/medication_dose.dart';

class MedicationScheduleState extends Equatable {
  final List<MedicationDose> todayDoses;

  const MedicationScheduleState({this.todayDoses = const []});

  int get completedCount => todayDoses.where((d) => d.isTaken).length;
  int get totalCount => todayDoses.length;
  double get adherenceRate =>
      totalCount > 0 ? (completedCount / totalCount) : 0.0;

  MedicationDose? get nextDueDose {
    final pending = todayDoses.where((d) => !d.isTaken).toList();
    return pending.isNotEmpty ? pending.first : null;
  }

  MedicationScheduleState copyWith({List<MedicationDose>? todayDoses}) {
    return MedicationScheduleState(todayDoses: todayDoses ?? this.todayDoses);
  }

  @override
  List<Object?> get props => [todayDoses];
}
