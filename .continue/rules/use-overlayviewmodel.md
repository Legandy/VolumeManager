---
globs: "**/*.kt"
description: Ensure that all references to OverlayViewModel are properly
  injected and used for managing overlay state
alwaysApply: true
---

Always use OverlayViewModel when accessing overlay-related data in OverlayService.kt