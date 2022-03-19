package com.example.drawingpad

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


//extending the main activity with AppCompact activity makes application compatiable with the older android versions
class MainActivity : AppCompatActivity() {
    private var drawingView : DrawingView? = null
    lateinit var mImageButtonCurrentPaint : ImageButton
    var customProgressDialog : Dialog? = null

    private var openGalleryLauncher:ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode == RESULT_OK && result.data!=null){
                val imageBackGround:ImageView = findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }
//accessing the permission from the user
    private val storageResultLauncher:ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()){
        permissions ->
        permissions.entries.forEach {
            val per :String = it.key
            val isGranted : Boolean = it.value
            if(isGranted){
                Toast.makeText(this,"Permission $per is granted!!",Toast.LENGTH_SHORT)
//                Permission is granted to the user
                val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }else{
                Toast.makeText(this,"Permission $per not granted!!",Toast.LENGTH_SHORT)
            }
        }
    }

//    action buttons
    lateinit var selectBrushBtn : ImageButton
    lateinit var undoBtn : ImageButton
    lateinit var redoBtn :ImageButton
    lateinit var saveBtn : ImageButton
    lateinit var galleryBtn : ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(5.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )

//        Init all action button int the view
        selectBrushBtn = findViewById(R.id.brush_btn)
        undoBtn = findViewById(R.id.undo_btn)
        saveBtn = findViewById(R.id.save_btn)
        galleryBtn = findViewById(R.id.gallery_btn)
        redoBtn = findViewById(R.id.redo_btn)

//        setting listeners to all the buttons
        selectBrushBtn.setOnClickListener {
            showBrushSizeDialog()
        }
        undoBtn.setOnClickListener {
            drawingView?.undoChange()
        }
        redoBtn.setOnClickListener {
            drawingView?.redoChange()
        }
        galleryBtn.setOnClickListener {
            requestPermission()
        }

        saveBtn.setOnClickListener {
            if(isReadPermissionAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView : FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    val bitmap = getBitmapFromView(flDrawingView)
                    saveImageToGallery(bitmap)
                }
            }
        }
    }

//    Permission Rational dialog
    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

// brush dialog
    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val verySmall : ImageButton = brushDialog.findViewById(R.id.ib_very_small_brush)
        val smallBtn:ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        val mediumBtn:ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        val  largeBtn:ImageButton = brushDialog.findViewById(R.id.ib_large_brush)

        verySmall.setOnClickListener {
            drawingView?.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

// on the paint clicked
    fun paintClicked(view: View) {
//        Toast.makeText(this, "Color changed!",Toast.LENGTH_LONG).show()
        if(view!==mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
//            calling the function in the view to change the color
            drawingView?.changeColor(colorTag)
//            current button will be assigned the on Pressed pallet
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_pressed
                )
            )
//            previous color onPressed pallet will be reverted
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_normal
                )
            )
//            current button will be changed and value will be stored
            mImageButtonCurrentPaint = view
        }
    }

//    get the bitmap from view to generate the image
    private fun getBitmapFromView(view:View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)

        return returnedBitmap;
    }

//    Function to save image to gallery
    private suspend fun saveImageToGallery(mBitmap: Bitmap?): String {
        var result =""
        withContext(Dispatchers.IO){
            if(mBitmap!=null){
                try{
                    var bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)
                    val f = File(externalCacheDir?.absolutePath.toString()
                            +
                            File.separator + "DrawingPad_"+System.currentTimeMillis()/1000+".jpg"
                    )

                    val fo =FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved at $result",
                                Toast.LENGTH_LONG
                            ).show()
                            shareFile(f)
                        } else {
                            Toast.makeText(
                                this@MainActivity ,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }catch (e:Exception){
                    Log.e("Sharing error : ",e.toString())
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
// check if we have a read permission
    private fun isReadPermissionAllowed():Boolean {
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }
//    request Permission
    private fun requestPermission() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
            Manifest.permission.READ_EXTERNAL_STORAGE)){
        showRationaleDialog(" Permission Demo requires camera access",
            "Camera cannot be used because Camera access is denied")
    }else{
        storageResultLauncher.launch(arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))
    }
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if(customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog=null
        }
    }

    private fun shareFile(result:File) {
        try {
            val fileUri: Uri? =
                FileProvider.getUriForFile(
                    this@MainActivity,
                    "com.example.drawingpad.fileprovider",
                    result)


                    Log.e("FILE PATH","$fileUri")
                    var shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    shareIntent.putExtra(Intent.EXTRA_TITLE,"Share your creation!!!")
                    shareIntent.putExtra(Intent.EXTRA_STREAM,fileUri)
                    shareIntent.type="image/jpg"
                    startActivity(Intent.createChooser(shareIntent,null))



        }catch(e:Exception){
            Log.e("Share File Error","$e")
        }

    }
}