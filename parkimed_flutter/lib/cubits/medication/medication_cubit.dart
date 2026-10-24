import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/models/medication_dose.dart';
import 'medication_state.dart';

class MedicationCubit extends Cubit<MedicationScheduleState> {
  MedicationCubit() : super(const MedicationScheduleState()) {
    loadSchedule();
  }

  void loadSchedule() {
    final doses = [
      const MedicationDose(
        id: 'dose_1',
        name: 'Levodopa / Carbidopa',
        dosage: '100mg / 25mg',
        scheduledTime: TimeOfDay(hour: 8, minute: 0),
        isTaken: true,
      ),
      const MedicationDose(
        id: 'dose_2',
        name: 'Levodopa / Carbidopa',
        dosage: '100mg / 25mg',
        scheduledTime: TimeOfDay(hour: 13, minute: 0),
        isTaken: false,
      ),
      const MedicationDose(
        id: 'dose_3',
        name: 'Pramipexole (Mirapex)',
        dosage: '0.5mg',
        scheduledTime: TimeOfDay(hour: 18, minute: 0),
        isTaken: false,
      ),
      const MedicationDose(
        id: 'dose_4',
        name: 'Levodopa Extended-Release',
        dosage: '200mg / 50mg',
        scheduledTime: TimeOfDay(hour: 22, minute: 0),
        isTaken: false,
      ),
    ];
    emit(state.copyWith(todayDoses: doses));
  }

  void toggleDoseTaken(String id) {
    final updated = state.todayDoses.map((dose) {
      if (dose.id == id) {
        final newStatus = !dose.isTaken;
        return dose.copyWith(
          isTaken: newStatus,
          takenAt: newStatus ? DateTime.now() : null,
        );
      }
      return dose;
    }).toList();

    emit(state.copyWith(todayDoses: updated));
  }
}
