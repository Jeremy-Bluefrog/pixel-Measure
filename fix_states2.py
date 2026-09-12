import re

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "r") as f:
    content = f.read()

content = content.replace("val viewMatrixState = viewModel.viewMatrix.collectAsState(initial = FloatArray(16))", "val viewMatrixState = viewModel.viewMatrix.collectAsState()")
content = content.replace("val projectionMatrixState = viewModel.projectionMatrix.collectAsState(initial = FloatArray(16))", "val projectionMatrixState = viewModel.projectionMatrix.collectAsState()")
content = content.replace("val liveTargetPointState = viewModel.liveTargetPoint.collectAsState(initial = null)", "val liveTargetPointState = viewModel.liveTargetPoint.collectAsState()")
content = content.replace("val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState(initial = null)", "val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState()")
content = content.replace("val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState(initial = com.example.ui.viewmodel.SensorTelemetry())", "val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState()")

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "w") as f:
    f.write(content)
