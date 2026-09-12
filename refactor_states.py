import re

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "r") as f:
    content = f.read()

# 1. Replace declarations
decl_replacements = {
    r'val viewMatrix by viewModel\.viewMatrix\.collectAsState\(\)': r'val viewMatrixState = viewModel.viewMatrix.collectAsState()',
    r'val projectionMatrix by viewModel\.projectionMatrix\.collectAsState\(\)': r'val projectionMatrixState = viewModel.projectionMatrix.collectAsState()',
    r'val liveDistanceMeters by viewModel\.liveDistanceMeters\.collectAsState\(\)': r'val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState()',
    r'val liveTargetPoint by viewModel\.liveTargetPoint\.collectAsState\(\)': r'val liveTargetPointState = viewModel.liveTargetPoint.collectAsState()',
    r'val sensorTelemetry by viewModel\.sensorTelemetry\.collectAsState\(\)': r'val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState()',
}

for k, v in decl_replacements.items():
    content = re.sub(k, v, content)

# 2. Replace usages
# We need to make sure we replace full words.
usage_replacements = {
    r'\bviewMatrix\b': r'viewMatrixState.value',
    r'\bprojectionMatrix\b': r'projectionMatrixState.value',
    r'\bliveDistanceMeters\b': r'liveDistanceMetersState.value',
    r'\bliveTargetPoint\b': r'liveTargetPointState.value',
    r'\bsensorTelemetry\b': r'sensorTelemetryState.value',
}

# Wait, we changed the declaration to viewMatrixState, so we don't want to replace viewMatrix in viewMatrixState.
# Let's do the usage replacement FIRST, then fix the declarations!

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "r") as f:
    content = f.read()

for k, v in usage_replacements.items():
    content = re.sub(k, v, content)

# Now fix the declarations which got messed up (e.g. viewMatrixState.valueState...)
decl_fixes = {
    r'val viewMatrixState\.value by viewModel\.viewMatrixState\.value\.collectAsState\(\)': r'val viewMatrixState = viewModel.viewMatrix.collectAsStateWithLifecycle()',
    r'val projectionMatrixState\.value by viewModel\.projectionMatrixState\.value\.collectAsState\(\)': r'val projectionMatrixState = viewModel.projectionMatrix.collectAsStateWithLifecycle()',
    r'val liveDistanceMetersState\.value by viewModel\.liveDistanceMetersState\.value\.collectAsState\(\)': r'val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsStateWithLifecycle()',
    r'val liveTargetPointState\.value by viewModel\.liveTargetPointState\.value\.collectAsState\(\)': r'val liveTargetPointState = viewModel.liveTargetPoint.collectAsStateWithLifecycle()',
    r'val sensorTelemetryState\.value by viewModel\.sensorTelemetryState\.value\.collectAsState\(\)': r'val sensorTelemetryState = viewModel.sensorTelemetry.collectAsStateWithLifecycle()',
}

for k, v in decl_fixes.items():
    content = re.sub(k, v, content)

# And fix line 284:
# val isMeasurementAvailable = trackingState == com.google.ar.core.TrackingState.TRACKING && liveTargetPointState.value != null
# We need to wrap it in a derivedStateOf if trackingState is not changing rapidly.
# But wait, if trackingState is read normally, and liveTargetPointState.value is read, we STILL read liveTargetPointState.value at top level!
# We MUST use derivedStateOf for isMeasurementAvailable!
content = content.replace(
    "val isMeasurementAvailable = trackingState == com.google.ar.core.TrackingState.TRACKING && liveTargetPointState.value != null",
    "val isMeasurementAvailable by remember { derivedStateOf { trackingState == com.google.ar.core.TrackingState.TRACKING && liveTargetPointState.value != null } }"
)

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "w") as f:
    f.write(content)
