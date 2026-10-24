import 'package:equatable/equatable.dart';
import 'package:flutter/material.dart';

class MedicationDose extends Equatable {
  final String id;
  final String name; // e.g. Levodopa / Carbidopa
  final String dosage; // e.g. 100mg / 25mg
  final TimeOfDay scheduledTime;
  final bool isTaken;
  final DateTime? takenAt;

  const MedicationDose({
    required this.id,
    required this.name,
    required this.dosage,
    required this.scheduledTime,
    this.isTaken = false,
    this.takenAt,
  });

  String get timeFormatted {
    final hour = scheduledTime.hourOfPeriod == 0 ? 12 : scheduledTime.hourOfPeriod;
    final minute = scheduledTime.minute.toString().padLeft(2, '0');
    final period = scheduledTime.period == DayPeriod.am ? 'AM' : 'PM';
    return '$hour:$minute $period';
  }

  MedicationDose copyWith({
    String? id,
    String? name,
    String? dosage,
    TimeOfDay? scheduledTime,
    bool? isTaken,
    DateTime? takenAt,
  }) {
    return MedicationDose(
      id: id ?? this.id,
      name: name ?? this.name,
      dosage: dosage ?? this.dosage,
      scheduledTime: scheduledTime ?? this.scheduledTime,
      isTaken: isTaken ?? this.isTaken,
      takenAt: takenAt ?? this.takenAt,
    );
  }

  @override
  List<Object?> get props => [id, name, dosage, scheduledTime, isTaken, takenAt];
}
