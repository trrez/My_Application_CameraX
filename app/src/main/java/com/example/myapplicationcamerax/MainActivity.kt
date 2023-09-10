package com.example.myapplicationcamerax

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Pantalla {
    FORM,
    CAMARA,
    UBICACION,
}

class AppVM : ViewModel() {
    val pantallaActual = mutableStateOf(Pantalla.FORM)

    var onPermisoCamaraOk: () -> Unit = {}

    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)

    var permisosUbicacionOk:() -> Unit = {}

    val imagenSeleccionada = mutableStateOf<Uri?>(null)
}

class FormRegistroVM : ViewModel() {
    val nombre = mutableStateOf("")
    val fotos = mutableStateOf<List<Uri>>(emptyList())
}

class MainActivity : ComponentActivity() {
    val appVM: AppVM by viewModels()
    val camaraVM: AppVM by viewModels()
    val formRegistroVM: FormRegistroVM by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[android.Manifest.permission.CAMERA] == true) {
            camaraVM.onPermisoCamaraOk()
        } else if (
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false) or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
        ) {
            appVM.permisosUbicacionOk()
        } else {
            Log.v("LanzadorPermisos callback", "Se denegaron los permisos")
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

    when (val pantallaActual = appVM.pantallaActual.value) {
        Pantalla.FORM -> {
            PantallaFormUI()
        }
        Pantalla.CAMARA -> {
            PantallaCamaraUI(lanzadorPermisos, cameraController)
        }
        Pantalla.UBICACION -> {
            PantallaUbicacionUI(appVM, lanzadorPermisos)
        }

        else -> {
            PantallaImagenCompleta(
                imagenUri = appVM.imagenSeleccionada.value,
                onClose = {
                    appVM.imagenSeleccionada.value = null // Cerrar la pantalla de imagen completa
                }
            )
        }
    }
    PantallaImagenCompleta(
        imagenUri = appVM.imagenSeleccionada.value,
        onClose = {
            appVM.imagenSeleccionada.value = null // Cerrar la pantalla de imagen completa
        }
    )
}



fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()

class FaltaPermisosException(mensaje:String): Exception(mensaje)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI() {
    val contexto = LocalContext.current
    val appVM: AppVM = viewModel()
    val formRegistroVM: FormRegistroVM = viewModel()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Campo de texto para el nombre
        TextField(
            value = formRegistroVM.nombre.value,
            onValueChange = { newValue ->
                formRegistroVM.nombre.value = newValue
            },
            label = { Text("Nombre del lugar visitado") },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        )

        // Mostrar el valor actual del campo de texto
        Text(
            text = "Nombre ingresado: ${formRegistroVM.nombre.value}",
            modifier = Modifier.padding(16.dp)
        )

        // Espacio vertical entre el campo de texto y el botón
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            appVM.pantallaActual.value = Pantalla.CAMARA
        }) {
            Text("Tomar foto")
        }

        Button(onClick = {
            appVM.pantallaActual.value = Pantalla.UBICACION
        }) {
            Text("Ubicacion")
        }

        // Mostrar las fotos capturadas
        formRegistroVM.fotos.value.forEachIndexed { index, uri ->
            val bitmap = uri2imageBitmap(uri, contexto)
            Image(
                painter = BitmapPainter(bitmap),
                contentDescription = "Imagen capturada $index",
                modifier = Modifier.clickable {
                    appVM.imagenSeleccionada.value = uri
                }
            )
        }
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
            formRegistroVM.nombre.value, // Pasa el nombre del lugar visitado
            contexto
        ) {
            formRegistroVM.fotos.value = formRegistroVM.fotos.value + it
            appVM.pantallaActual.value = Pantalla.FORM
        }
    }) {
        Text("Tomar Foto")
    }
}

fun generarNombreUnico(): String {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "IMG_$timeStamp"
}

fun capturarFotografia(
    cameraController: LifecycleCameraController,
    nombreLugar: String,
    contexto: Context,
    onImagenGuardada: (uri: Uri) -> Unit
) {
    val directorioFotos = File(contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES), nombreLugar)
    directorioFotos.mkdirs()

    val nombreFoto = generarNombreUnico() + ".jpg"
    val archivoFoto = File(directorioFotos, nombreFoto)

    val opciones = ImageCapture.OutputFileOptions.Builder(archivoFoto).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    Log.v("etiqueta", "URI = $it")
                    onImagenGuardada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("capturarFotografia::OnImageSavedCallback::onError", exception.message ?: "error")
            }
        }
    )
}


fun conseguirUbicacion(contexto: Context, onSuccess:(ubicacion: Location)-> Unit){
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )
        tarea.addOnSuccessListener {
            onSuccess(it)
        }
    }catch (se:SecurityException){
        throw FaltaPermisosException("Sin Permisos de ubicacion")
    }

}

@Composable
fun PantallaUbicacionUI(appVM:AppVM, lanzadorPermisos:ActivityResultLauncher<Array<String>> ){
    val contexto = LocalContext.current


    Column (){
        Button(onClick = {
            appVM.permisosUbicacionOk = {
                conseguirUbicacion(contexto) {
                    appVM.latitud.value = it.latitude
                    appVM.longitud.value = it.longitude
                }
            }


            lanzadorPermisos.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

        }) {
            Text("Conseguir Ubicacion")
        }
        Text("Lat: ${appVM.latitud.value} Long: ${appVM.longitud.value}")
        Button(onClick = {
            appVM.pantallaActual.value = Pantalla.FORM
        }) {
            Text("Regresar")
        }
        Spacer(Modifier.height(100.dp))



        AndroidView(
            factory = {
                MapView(it).apply  {
                    setTileSource(TileSourceFactory.MAPNIK)

                    org.osmdroid.config.Configuration.getInstance().userAgentValue =
                        contexto.packageName
                    controller.setZoom(15.0)
                }
            }, update = {
                it.overlays.removeIf{ true }
                it.invalidate()

                val geoPoint = GeoPoint(appVM.latitud.value, appVM.longitud.value)
                it.controller.animateTo(geoPoint)

                val marcador = Marker(it)
                marcador.position = geoPoint
                marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                it.overlays.add(marcador)
            }
        )
    }
}


@Composable
fun PantallaImagenCompleta(
    imagenUri: Uri?,
    onClose: () -> Unit
) {
    if (imagenUri != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable { onClose() }
        ) {
            Image(
                painter = rememberImagePainter(data = imagenUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
