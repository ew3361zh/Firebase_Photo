package com.nikosnockoffs.android.collage_photo

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "COLLAGE_ACTIVITY"
class MainActivity : AppCompatActivity() {

    private lateinit var imageButton1: ImageButton
    private lateinit var uploadImageFab: FloatingActionButton
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var mainView: View

    private var newPhotoPath: String? = null
    private var visibleImagePath: String? = null
    private var imageFilename: String? = null
    private var photoUri: Uri? = null

    private val NEW_PHOTO_PATH_KEY = "new photo path key"
    private val VISIBLE_IMAGE_PATH_KEY = "visible image path key"

    private val storage = Firebase.storage

    private val cameraActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleImage(result)
    }

    private fun handleImage(result: ActivityResult) {
        // check result - see what user did when the camera app opened
        when (result.resultCode) {
            RESULT_OK -> {
                Log.d(TAG, "Result ok, image at $newPhotoPath")
                visibleImagePath = newPhotoPath
            }
            RESULT_CANCELED -> {
                Log.d(TAG, "Result cancelled, no picture taken")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(NEW_PHOTO_PATH_KEY, newPhotoPath)
        outState.putString(VISIBLE_IMAGE_PATH_KEY, visibleImagePath)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "on window focus changed $hasFocus visible image at $visibleImagePath")
        if (hasFocus) {
            visibleImagePath?.let { imagePath ->
                loadImage(imageButton1, imagePath) }
        }
    }

    private fun loadImage(imageButton: ImageButton, imagePath: String) {
        // want to fit image into the image button size but can vary based on device/orientation
        Picasso.get()
            .load(File(imagePath))
            .error(android.R.drawable.stat_notify_error) // displayed if issue with loading image
            .fit()
            .centerCrop()
            .into(imageButton, object: Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Loaded image $imagePath")
                }

                override fun onError(e: Exception?) {
                    Log.e(TAG, "Error loading image $imagePath", e)
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        newPhotoPath = savedInstanceState?.getString(NEW_PHOTO_PATH_KEY)
        visibleImagePath = savedInstanceState?.getString(VISIBLE_IMAGE_PATH_KEY)
        mainView = findViewById(R.id.content)
        uploadProgressBar = findViewById(R.id.upload_progress_bar)
        uploadImageFab = findViewById(R.id.upload_image_button)

        uploadImageFab.setOnClickListener {
            uploadImage()
        }

        imageButton1 = findViewById(R.id.imageButton1)
        imageButton1.setOnClickListener {
            takePicture()
        }

    }

    // made photoUri and filename global variables
    private fun uploadImage() {
        if (photoUri != null && imageFilename != null) {
            uploadProgressBar.visibility = View.VISIBLE

            val imageStorageRootReference = storage.reference
            val imageCollectionReference = imageStorageRootReference.child("images")
            val imageFileReference = imageCollectionReference.child(imageFilename!!)

            imageFileReference.putFile(photoUri!!).addOnCompleteListener {
                Snackbar.make(mainView, "Image uploaded!", Snackbar.LENGTH_LONG).show()
                uploadProgressBar.visibility = View.GONE
            }
                .addOnFailureListener { error ->
                    Snackbar.make(mainView, "Image NOT uploaded - error!", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG,"Error uploading image $imageFilename", error)
                    uploadProgressBar.visibility = View.GONE
                }

        } else {
            Snackbar.make(mainView, "Take a picture first!", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun takePicture() {
        // implicit intent to launch camera app when this function is called by pressing the button
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // create reference to file want image to be saved in
        val (photoFile, photoFilePath) = createImageFile()
        if (photoFile != null) {
            newPhotoPath = photoFilePath
            // create Uri for new photofile created - bc camera app works with Uris
            photoUri = FileProvider.getUriForFile(
                this,
                "com.nikosnockoffs.android.collage_photo.fileprovider",
                photoFile
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            cameraActivityLauncher.launch(takePictureIntent)
        }

    }

    private fun createImageFile(): Pair<File?, String?> {
        try {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            imageFilename = "COLLAGE_${dateTime}"
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            // file is reference to file itself
            val file = File.createTempFile(imageFilename, ".jpg", storageDir)
            // filePath is string location of where the file is on the device
            val filePath = file.absolutePath
            return file to filePath

        } catch (ex: IOException) {
            // return pair with "to"
            return null to null
        }
    }
}