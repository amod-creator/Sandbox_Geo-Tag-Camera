package com.amod.geotagcamera

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.amod.geotagcamera.AgriPssPdfGenerator

import android.graphics.Rect
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.os.Environment
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.amod.geotagcamera.databinding.StaffInputBinding

class StaffInputActivity : AppCompatActivity() {
    private lateinit var binding: StaffInputBinding
    private var selectedFormat: String = "VISIT REPORT"  // Default format

    // Photo handling
    private val loadedPhotos = mutableListOf<android.graphics.Bitmap>()
    private val photoPaths = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = StaffInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load photos from file paths
        loadPhotosFromPaths()

        // Initialize format spinner with a custom layout for items
        val formats = listOf("VISIT REPORT", "AGRI PSS")
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            formats
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.formatSpinner.adapter = adapter

        // Set default selection
        binding.formatSpinner.setSelection(0)

        binding.formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                selectedFormat = formats[pos]
                if (selectedFormat == "VISIT REPORT") {
                    binding.visitReportLayout.visibility = android.view.View.VISIBLE
                    binding.agriPssLayout.visibility = android.view.View.GONE
                } else {
                    binding.visitReportLayout.visibility = android.view.View.GONE
                    binding.agriPssLayout.visibility = android.view.View.VISIBLE
                    val sel = binding.collateralObtainedSpinner.selectedItem?.toString()?.trim()?.lowercase()
                    if (sel == "no") applyCollateralNAState() else if (sel == "yes") applyCollateralEditableState()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Keep default selection
            }
        }

        // Back button
        binding.backToFormButton.setOnClickListener {
            finish()
        }

        setupCollateralObtainedWatcher()

        // Set up scroll behavior for keyboard visibility
        val formScroll = binding.staffScrollView
        val remarksInput = binding.remarksInput

        // Add global layout listener to handle keyboard appearance
        formScroll.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            formScroll.getWindowVisibleDisplayFrame(rect)
            val screenHeight = formScroll.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // If keyboard is shown (takes up more than 15% of screen)
            if (keypadHeight > screenHeight * 0.15) {
                // Calculate and scroll to show the focused input
                formScroll.postDelayed({
                    val focusedView = currentFocus
                    if (focusedView != null) {
                        val scrollTo = focusedView.top - (formScroll.height / 4)
                        formScroll.smoothScrollTo(0, scrollTo)
                    }
                }, 250) // Slight delay to ensure proper scrolling
            }
        }

        // Handle remarks field focus specifically
        remarksInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                formScroll.postDelayed({
                    val scrollTo = remarksInput.top - (formScroll.height / 4)
                    formScroll.smoothScrollTo(0, scrollTo)
                }, 250)
            }
        }

        binding.submitButton.setOnClickListener {
            if (selectedFormat == "AGRI PSS") {
                val applicantName = binding.applicantNameInput.text.toString()
                val cifNo = binding.cifNoInput.text.toString()
                val constitution = binding.constitutionSpinner.selectedItem.toString()
                val fatherName = binding.fatherNameInput.text.toString()
                val natureOfApplicant = binding.applicantNatureSpinner.selectedItem.toString()
                val annexureAttached = binding.attachedAnnexureSpinner.selectedItem.toString()
                val collateralAccessible = binding.collateralObtainedSpinner.selectedItem.toString()
                val collateralNature = binding.propertyNatureInput.text.toString()
                val collateralAddress = binding.collateralAddressInput.text.toString()
                val collateralDemarcated = when {
                    binding.collateralDemarcatedYes.isChecked -> "Yes"
                    binding.collateralDemarcatedNo.isChecked -> "No"
                    else -> ""
                }

                val residenceAddress = binding.residenceAddressInput.text.toString()
                val residenceVerified = when {
                    binding.residenceVerifiedYes.isChecked -> "Yes"
                    binding.residenceVerifiedNo.isChecked -> "No"
                    else -> ""
                }
                val residencePersonMet = binding.residencePersonMetInput.text.toString()
                val residenceRelation = "" // not captured in UI

                val workplaceAddress = binding.workplaceAddressInput.text.toString()
                val workplaceVerified = when {
                    binding.workplaceVerifiedYes.isChecked -> "Yes"
                    binding.workplaceVerifiedNo.isChecked -> "No"
                    else -> ""
                }
                val workplacePersonMet = binding.workplacePersonMetInput.text.toString()
                val workplaceRelation = "" // not captured in UI

                val remarks = binding.additionalRemarksInput.text.toString()
                val place = binding.branchInput.text.toString()

                val collateralObtained = binding.collateralObtainedSpinner.selectedItem.toString()
                val keyPersonName = binding.keyPersonInput.text.toString()
                val keyPersonContact = "" // TODO: bind actual contact field if present (e.g., keyPersonContactInput)
                val guarantorName = binding.guarantorNamesInput.text.toString()
                val collateralVerified = when {
                    binding.collateralAddressVerifiedYes.isChecked -> "Yes"
                    binding.collateralAddressVerifiedNo.isChecked -> "No"
                    else -> ""
                }
                val dispute = when {
                    binding.disputesYes.isChecked -> "Yes"
                    binding.disputesNo.isChecked -> "No"
                    else -> ""
                }
                val otherLoan = when {
                    binding.otherLoansYes.isChecked -> "Yes"
                    binding.otherLoansNo.isChecked -> "No"
                    else -> ""
                }
                val designation = binding.designationInput.text.toString()
                val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                val input = AgriPssPdfGenerator.Input(
                    applicantName = applicantName,
                    cifNo = cifNo,
                    constitution = constitution,
                    fatherName = fatherName,
                    natureOfApplicant = natureOfApplicant,
                    annexureAttached = annexureAttached,
                    collateralAccessible = collateralAccessible,
                    collateralNature = collateralNature,
                    collateralAddress = collateralAddress,
                    collateralDemarcated = collateralDemarcated,
                    residenceAddress = residenceAddress,
                    residenceVerified = residenceVerified,
                    residencePersonMet = residencePersonMet,
                    residenceRelation = residenceRelation,
                    workplaceAddress = workplaceAddress,
                    workplaceVerified = workplaceVerified,
                    workplacePersonMet = workplacePersonMet,
                    workplaceRelation = workplaceRelation,
                    remarks = remarks,
                    place = place,
                    collateralObtained = collateralObtained,
                    collateralVerified = collateralVerified,
                    date = date,
                    designation = designation,
                    dispute = dispute,
                    otherLoan = otherLoan,
                    guarantorName = guarantorName,
                    keyPersonContact = keyPersonContact,
                    keyPersonName = keyPersonName,
                    officialName = binding.nameInput.text.toString()
                )

                val pdfFileName = "AgriPssReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"

                try {
                    var finalUri: Uri? = null

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val resolver = contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/GPS Cam Visit Pro")
                        }
                        
                        // Use external content uri for files
                        val fileUri = android.provider.MediaStore.Files.getContentUri("external")
                        finalUri = resolver.insert(fileUri, contentValues)
                        
                        finalUri?.let { uri ->
                            resolver.openOutputStream(uri)?.use { out ->
                                AgriPssPdfGenerator(this@StaffInputActivity).generate(input, out)
                            }
                        }
                    } else {
                        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        val pdfDir = java.io.File(docsDir, "GPS Cam Visit Pro")
                        if (!pdfDir.exists()) {
                            pdfDir.mkdirs()
                        }
                        val outputFile = java.io.File(pdfDir, pdfFileName)
                        java.io.FileOutputStream(outputFile).use { out ->
                            AgriPssPdfGenerator(this@StaffInputActivity).generate(input, out)
                        }
                        
                        finalUri = FileProvider.getUriForFile(
                            this@StaffInputActivity,
                            "${applicationContext.packageName}.fileprovider",
                            outputFile
                        )
                    }

                    if (finalUri != null) {
                        Toast.makeText(this, "AGRI PSS Report saved to Documents/GPS Cam Visit Pro", Toast.LENGTH_LONG).show()

                        // IMPORTANT: MediaStore URIs are often rejected by Google Drive.
                        // We must proxy it securely through FileProvider for universal opening.
                        val tempFile = java.io.File(cacheDir, "temp_agri_pss.pdf")
                        contentResolver.openInputStream(finalUri!!)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val viewUri = FileProvider.getUriForFile(
                            this@StaffInputActivity,
                            "${applicationContext.packageName}.fileprovider",
                            tempFile
                        )

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(viewUri, "application/pdf")
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        try {
                            startActivity(Intent.createChooser(intent, "Open PDF with..."))
                        } catch (e: android.content.ActivityNotFoundException) {
                            Toast.makeText(this, "No PDF viewer app found. Please install Google Drive or a PDF viewer.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                         Toast.makeText(this, "Failed to create PDF file.", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("StaffInputActivity", "Error generating PDF", e)
                }
            } else {
                // Generate Visit Report PDF
                // Generate Visit Report PDF
                val input = VisitReportPdfGenerator.Input(
                    staffName = binding.nameInput.text.toString(),
                    pfNumber = binding.pfInput.text.toString(),
                    designation = binding.designationInput.text.toString(),
                    branchName = binding.branchInput.text.toString(),
                    branchCode = binding.branchCodeInput.text.toString(),
                    loanAccountNumber = binding.loanAccInput.text.toString(),
                    borrowerName = binding.loanNameInput.text.toString(),
                    loanAmount = binding.loanAmountInput.text.toString(),
                    borrowerAddress = binding.borrowerAddressInput.text.toString(),
                    mobileNumber = binding.borrowerMobileInput.text.toString(),
                    activity = binding.activityInput.text.toString(),
                    observations = binding.remarksInput.text.toString(),
                    visitDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                )

                val pdfFileName = "${input.borrowerName.replace(" ", "_").ifEmpty { "Report" }}_${Date().time}_VisitReport.pdf"
                
                try {
                    var finalUri: Uri? = null

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val resolver = contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/GPS Cam Visit Pro")
                        }
                        
                        val fileUri = android.provider.MediaStore.Files.getContentUri("external")
                        finalUri = resolver.insert(fileUri, contentValues)
                        
                        finalUri?.let { uri ->
                            resolver.openOutputStream(uri)?.use { out ->
                                val outputFile = java.io.File(cacheDir, "temp_visit.pdf")
                                VisitReportPdfGenerator(this@StaffInputActivity).generate(input, loadedPhotos, outputFile)
                                outputFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                    } else {
                        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        val pdfDir = java.io.File(docsDir, "GPS Cam Visit Pro")
                        if (!pdfDir.exists()) {
                            pdfDir.mkdirs()
                        }
                        val outputFile = java.io.File(pdfDir, pdfFileName)
                        VisitReportPdfGenerator(this@StaffInputActivity).generate(input, loadedPhotos, outputFile)
                        
                        finalUri = FileProvider.getUriForFile(
                            this@StaffInputActivity,
                            "${applicationContext.packageName}.fileprovider",
                            outputFile
                        )
                    }

                    if (finalUri != null) {
                        Toast.makeText(this, "Visit Report saved to Documents/GPS Cam Visit Pro", Toast.LENGTH_LONG).show()

                        val tempFile = java.io.File(cacheDir, "temp_visit_report.pdf")
                        contentResolver.openInputStream(finalUri!!)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val viewUri = FileProvider.getUriForFile(
                            this@StaffInputActivity,
                            "${applicationContext.packageName}.fileprovider",
                            tempFile
                        )

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(viewUri, "application/pdf")
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        try {
                            startActivity(Intent.createChooser(intent, "Open PDF with..."))
                        } catch (e: android.content.ActivityNotFoundException) {
                            Toast.makeText(this, "No PDF viewer app found. Please install Google Drive or a PDF viewer.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                         Toast.makeText(this, "Failed to create PDF file.", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Error saving Visit Report: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("StaffInputActivity", "Error generating Visit Report", e)
                }
            }
        }
    }

    private fun applyCollateralNAState() {
        val natureEt = binding.propertyNatureInput
        val addressEt = binding.collateralAddressInput
        val demarcationGroup = binding.collateralDemarcationGroup
        val accessibleGroup = binding.collateralAccessibilityGroup
        val verifiedGroup = binding.collateralAddressVerificationGroup
        val demarcNA = binding.collateralDemarcatedNA
        val accessNA = binding.collateralAccessibleNA
        val verifiedNA = binding.collateralAddressVerifiedNA

        natureEt.setText("Not applicable")
        addressEt.setText("Not applicable")
        natureEt.isEnabled = false
        addressEt.isEnabled = false
        demarcationGroup.check(demarcNA.id)
        accessibleGroup.check(accessNA.id)
        verifiedGroup.check(verifiedNA.id)
        for (i in 0 until demarcationGroup.childCount) demarcationGroup.getChildAt(i).isEnabled = false
        for (i in 0 until accessibleGroup.childCount) accessibleGroup.getChildAt(i).isEnabled = false
        for (i in 0 until verifiedGroup.childCount) verifiedGroup.getChildAt(i).isEnabled = false
    }

    private fun applyCollateralEditableState() {
        val natureEt = binding.propertyNatureInput
        val addressEt = binding.collateralAddressInput
        val demarcationGroup = binding.collateralDemarcationGroup
        val accessibleGroup = binding.collateralAccessibilityGroup
        val verifiedGroup = binding.collateralAddressVerificationGroup

        natureEt.isEnabled = true
        addressEt.isEnabled = true
        if (natureEt.text.toString().equals("Not applicable", true)) natureEt.setText("")
        if (addressEt.text.toString().equals("Not applicable", true)) addressEt.setText("")
        for (i in 0 until demarcationGroup.childCount) demarcationGroup.getChildAt(i).isEnabled = true
        for (i in 0 until accessibleGroup.childCount) accessibleGroup.getChildAt(i).isEnabled = true
        for (i in 0 until verifiedGroup.childCount) verifiedGroup.getChildAt(i).isEnabled = true
        demarcationGroup.clearCheck()
        accessibleGroup.clearCheck()
        verifiedGroup.clearCheck()
    }

    private fun setupCollateralObtainedWatcher() {
        val spinner = binding.collateralObtainedSpinner

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                when (parent.getItemAtPosition(position)?.toString()?.trim()?.lowercase()) {
                    "no" -> applyCollateralNAState()
                    "yes" -> applyCollateralEditableState()
                    else -> { /* do nothing */ }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { /* no-op */ }
        }

        // Initialize once based on current selection
        val initial = spinner.selectedItem?.toString()?.trim()?.lowercase()
        if (initial == "no") applyCollateralNAState() else if (initial == "yes") applyCollateralEditableState()
    }

    /**
     * Load photos from file paths provided by MainActivity
     * Converts JPEG files back to Bitmaps for use in the form
     */
    private fun loadPhotosFromPaths() {
        // Get photo paths from Intent
        val paths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: emptyList()

        photoPaths.clear()
        loadedPhotos.clear()

        paths.forEach { path ->
            try {
                // Load bitmap from file
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    loadedPhotos.add(bitmap)
                    photoPaths.add(path)
                    Log.d("StaffInputActivity", "Loaded photo from: $path")
                } else {
                    Log.w("StaffInputActivity", "Failed to decode bitmap from: $path")
                }
            } catch (e: Exception) {
                Log.e("StaffInputActivity", "Error loading photo from $path: ${e.message}")
            }
        }

        Log.d("StaffInputActivity", "Loaded ${loadedPhotos.size} photos successfully")
    }



    /**
     * Clean up temporary photo files when activity is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        // Delete temporary files
        photoPaths.forEach { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                    Log.d("StaffInputActivity", "Deleted temp file: $path")
                }
            } catch (e: Exception) {
                Log.e("StaffInputActivity", "Error deleting temp file $path: ${e.message}")
            }
        }
        // Recycle bitmaps to free memory
        loadedPhotos.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
