import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

/// Service for generating and manipulating clone icons.
class IconService {
  /// Generate a tinted version of the original icon with a color overlay + badge number.
  static Future<Uint8List?> generateCloneIcon({
    required Uint8List originalIcon,
    required int cloneIndex,
    Color tintColor = Colors.blue,
  }) async {
    // For now, return null — the native side will handle icon overlay
    // In future: use Canvas to draw overlay badge on icon
    return null;
  }

  /// Predefined color options for clone icons.
  static const List<Color> iconColors = [
    Color(0xFF6C5CE7), // Purple
    Color(0xFF00B894), // Green
    Color(0xFFE17055), // Orange
    Color(0xFF0984E3), // Blue
    Color(0xFFD63031), // Red
    Color(0xFFFDCB6E), // Yellow
    Color(0xFFE84393), // Pink
    Color(0xFF00CEC9), // Teal
  ];

  /// Get a default color for a given clone index.
  static Color getDefaultColor(int cloneIndex) {
    return iconColors[(cloneIndex - 1) % iconColors.length];
  }
}
