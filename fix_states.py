import re

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "r") as f:
    content = f.read()

# Fix collectAsStateWithLifecycle -> collectAsState
content = content.replace("collectAsStateWithLifecycle()", "collectAsState()")

# Wait, the issue with `kotlin.Any` might be because in Python script I replaced `viewMatrix` with `viewMatrixState.value`, which applies to `viewMatrix.size` -> `viewMatrixState.value.size`
# BUT wait! FloatArray's size is a property `size`, so `viewMatrixState.value.size` should be fine.
# Why did it complain about `Function invocation 'size()' expected`?
# Ah! In Kotlin, `.size` on FloatArray is a property. But wait, if it's `viewMatrixState.value.size` and `viewMatrixState.value` is being interpreted as Any, maybe `collectAsState` lost generics?
# Let's add explicit types to the declarations!
content = content.replace("val viewMatrixState = viewModel.viewMatrix.collectAsState()", "val viewMatrixState = viewModel.viewMatrix.collectAsState(initial = FloatArray(16))")
content = content.replace("val projectionMatrixState = viewModel.projectionMatrix.collectAsState()", "val projectionMatrixState = viewModel.projectionMatrix.collectAsState(initial = FloatArray(16))")
content = content.replace("val liveTargetPointState = viewModel.liveTargetPoint.collectAsState()", "val liveTargetPointState = viewModel.liveTargetPoint.collectAsState(initial = null)")
content = content.replace("val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState()", "val liveDistanceMetersState = viewModel.liveDistanceMeters.collectAsState(initial = null)")
content = content.replace("val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState()", "val sensorTelemetryState = viewModel.sensorTelemetry.collectAsState(initial = com.example.ui.viewmodel.SensorTelemetry())")

with open("app/src/main/java/com/example/ui/components/ModernArCameraView.kt", "w") as f:
    f.write(content)
