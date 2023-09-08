package com.example.myapplicationcamerax

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.LocalDateTime

enum class Pantalla {
    FORM,
    CAMARA
}

class AppVM : ViewModel() {
    val pantallaActual = mutableStateOf(Pantalla.FORM)

    var onPermisoCamaraOk: () -> Unit = {}
}

class FormRegistroVM : ViewModel() {
    val nombre = mutableStateOf("")
    val foto = mutableStateOf<Uri?>(null)
}

class MainActivity : ComponentActivity() {

    val camaraVM: AppVM by viewModels()
    val formRegistroVM: FormRegistroVM by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[android.Manifest.permission.CAMERA] == true) {
            camaraVM.onPermisoCamaraOk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        setContent {
            AppUI(lanzadorPermisos, cameraController)
        }
    }
}

@Composable
fun AppUI(lanzadorPermisos: ActivityResultLauncher<Array<String>>, cameraController: LifecycleCameraController) {
    val appVM: AppVM = viewModel()

    when (appVM.pantallaActual.value) {
        Pantalla.FORM -> {
            PantallaFormUI()
        }
        Pantalla.CAMARA -> {
            PantallaCamaraUI(lanzadorPermisos, cameraController)
        }
    }
}

fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI() {
    val contexto = LocalContext.current
    val appVM: AppVM = viewModel()
    val formRegstroVM: FormRegistroVM = viewModel()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Campo de texto para el nombre
        TextField(
            value = formRegstroVM.nombre.value,
            onValueChange = { newValue ->
                formRegstroVM.nombre.value = newValue
            },
            label = { Text("Nombre del lugar visitado") },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        )

        // Mostrar el valor actual del campo de texto
        Text(
            text = "Nombre ingresado: ${formRegstroVM.nombre.value}",
            modifier = Modifier.padding(16.dp)
        )

        // Espacio vertical entre el campo de texto y el botón
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            appVM.pantallaActual.value = Pantalla.CAMARA
        }) {
            Text("Tomar foto")
        }
    }
    formRegstroVM.foto.value?.let {
        Image(
            painter = BitmapPainter(uri2imageBitmap(it, contexto)),
            contentDescription = "Imagen capturada desde cameraX"
        )
    }
}

@Composable
fun PantallaCamaraUI(
    lanzadorPermisos: ActivityResultLauncher<Array<String>>,
    cameraController: LifecycleCameraController
) {
    val contexto = LocalContext.current
    val formRegistroVM: FormRegistroVM = viewModel()
    val appVM: AppVM = viewModel()

    lanzadorPermisos.launch(arrayOf(android.Manifest.permission.CAMERA))

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        }
    )
    Button(onClick = {
        capturarFotografia(
            cameraController,
            crearArchivoImagenPrivada(contexto),
            contexto
        ) {
            formRegistroVM.foto.value = it
            appVM.pantallaActual.value = Pantalla.FORM
        }
    }) {
        Text("Sacar Foto")
    }
}

fun generarNombreSegunFechaHastaSegundo(): String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

fun crearArchivoImagenPrivada(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun capturarFotografia(
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onImagenGuardada: (uri: Uri) -> Unit
) {
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    onImagenGuardada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("capturarFotografia::OnImageSavedCallback::onError", exception.message ?: "error")
            }
        }
    )
}
