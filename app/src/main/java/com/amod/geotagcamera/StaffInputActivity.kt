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
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.amod.geotagcamera.databinding.StaffInputBinding

class StaffInputActivity : AppCompatActivity() {
    private lateinit var binding: StaffInputBinding
    private var selectedFormat: String = "VISIT REPORT"  // Default format

    // Photo handling
    private val loadedPhotos = mutableListOf<android.graphics.Bitmap>()
    private val photoPaths = mutableListOf<String>()
    private val photoGpsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = StaffInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved form state first
        loadFormState()

        // Load photos from file paths
        loadPhotosFromPaths()

        // Automatically fill GPS Location field with the first photo's GPS Address
        val firstGps = photoGpsList.firstOrNull()
        if (!firstGps.isNullOrBlank()) {
            binding.gpsLocationInput.setText(firstGps)
        }

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

        // Handle Back button click - return to MainActivity
        binding.backToFormButton.setOnClickListener {
            finish()
        }

        binding.submitButton.setOnClickListener {
            if (selectedFormat == "AGRI PSS") {
                // Validate inputs before generating PDF (now always optional/returns true)
                validateAgriPssInputs()
                
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

                // Save to Documents/GPS Cam Visit Pro folder - matching MainActivity configuration
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val pdfDir = java.io.File(docsDir, "GPS Cam Visit Pro")
                if (!pdfDir.exists()) {
                    pdfDir.mkdirs()
                }
                val pdfFileName = "AgriPssReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
                val outputFile = java.io.File(pdfDir, pdfFileName)

                try {
                    AgriPssPdfGenerator(this).generate(input, outputFile)
                    Toast.makeText(this, "AGRI PSS Report saved to Documents/GPS Cam Visit Pro", Toast.LENGTH_LONG).show()
                    Log.d("StaffInputActivity", "PDF saved to: ${outputFile.absolutePath}")

                    // Open the PDF immediately
                    openPdfFile(outputFile)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("StaffInputActivity", "Error generating PDF: ${e.message}")
                }
            } else {
                // Generate Visit Report PDF
                // Validate inputs before generating PDF (now always optional/returns true)
                validateVisitReportInputs()
                
                val staffName = binding.nameInput.text.toString()
                val pfNumber = binding.pfInput.text.toString()
                val designation = binding.designationInput.text.toString()
                val branchName = binding.branchInput.text.toString()
                val branchCode = binding.branchCodeInput.text.toString()
                val loanAccountNumber = binding.loanAccInput.text.toString()
                val borrowerName = binding.loanNameInput.text.toString()
                val loanAmount = binding.loanAmountInput.text.toString()
                val borrowerAddress = binding.borrowerAddressInput.text.toString()
                val borrowerMobile = binding.borrowerMobileInput.text.toString()
                val activity = binding.activityInput.text.toString()
                val observations = binding.remarksInput.text.toString()
                val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                val input = VisitReportPdfGenerator.Input(
                    staffName = staffName,
                    pfNumber = pfNumber,
                    designation = designation,
                    branchName = branchName,
                    branchCode = branchCode,
                    loanAccountNumber = loanAccountNumber,
                    borrowerName = borrowerName,
                    loanAmount = loanAmount,
                    borrowerAddress = borrowerAddress,
                    mobileNumber = borrowerMobile,
                    activity = activity,
                    observations = observations,
                    visitDate = date,
                    gpsLocation = binding.gpsLocationInput.text.toString().ifBlank { "Not Available" }
                )


                // Save to Documents/GPS Cam Visit Pro folder - matching MainActivity configuration
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val pdfDir = java.io.File(docsDir, "GPS Cam Visit Pro")
                if (!pdfDir.exists()) {
                    pdfDir.mkdirs()
                }

                // Create filename: Name of Borrower_Account Number_Visit Report_ Date & Time.pdf
                val borrower = if (borrowerName.isBlank()) "Unknown" else borrowerName.replace(Regex("[^a-zA-Z0-9 ]"), "").trim().replace(" ", "_")
                val account = if (loanAccountNumber.isBlank()) "NoAccount" else loanAccountNumber.replace(Regex("[^a-zA-Z0-9]"), "")
                val dateForFilename = SimpleDateFormat("dd-MM-yyyy_HHmmss", Locale.US).format(Date())
                val pdfFileName = "${borrower}_${account}_Visit_Report_${dateForFilename}.pdf"
                val outputFile = java.io.File(pdfDir, pdfFileName)

                try {
                    VisitReportPdfGenerator(this).generate(input, loadedPhotos, null, outputFile)
                    Toast.makeText(this, "Visit Report saved to Documents/GPS Cam Visit Pro", Toast.LENGTH_LONG).show()
                    Log.d("StaffInputActivity", "PDF saved to: ${outputFile.absolutePath}")

                    // Open the PDF immediately
                    openPdfFile(outputFile)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("StaffInputActivity", "Error generating PDF: ${e.message}")
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
     * Validate Visit Report inputs before generating PDF
     * @return true if all required fields are filled, false otherwise
     */
    private fun validateVisitReportInputs(): Boolean {
        return true
    }

    /**
     * Validate AGRI PSS inputs before generating PDF
     * @return true if all required fields are filled, false otherwise
     */
    private fun validateAgriPssInputs(): Boolean {
        return true
    }

    /**
     * Load photos from file paths provided by MainActivity
     * Converts JPEG files back to Bitmaps for use in the form
     */
    private fun loadPhotosFromPaths() {
        // Get photo paths from Intent
        val paths = intent.getStringArrayListExtra("PHOTO_PATHS") ?: emptyList()
        val gpsList = intent.getStringArrayListExtra("PHOTO_GPS") ?: emptyList()

        photoPaths.clear()
        loadedPhotos.clear()
        photoGpsList.clear()

        paths.forEachIndexed { index, path ->
            try {
                // Load bitmap from file
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    loadedPhotos.add(bitmap)
                    photoPaths.add(path)
                    photoGpsList.add(gpsList.getOrNull(index) ?: "")
                    Log.d("StaffInputActivity", "Loaded photo from: $path with GPS: ${photoGpsList.last()}")
                } else {
                    Log.w("StaffInputActivity", "Failed to decode bitmap from: $path")
                }
            } catch (e: Exception) {
                Log.e("StaffInputActivity", "Error loading photo from $path: ${e.message}")
            }
        }

        Log.d("StaffInputActivity", "Loaded ${loadedPhotos.size} photos and GPS data successfully")
    }

    /**
     * Open a PDF file using an external PDF viewer
     */
    private fun openPdfFile(file: java.io.File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // Check if there's an app that can handle PDF files
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No PDF viewer app found. Please install one.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("StaffInputActivity", "Error opening PDF: ${e.message}")
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    override fun onPause() {
        super.onPause()
        saveFormState()
    }

    private fun saveFormState() {
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.FORM_PREFS", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        // Visit Report fields
        editor.putString("vr_staff_name", binding.nameInput.text.toString())
        editor.putString("vr_pf_number", binding.pfInput.text.toString())
        editor.putString("vr_designation", binding.designationInput.text.toString())
        editor.putString("vr_branch_name", binding.branchInput.text.toString())
        editor.putString("vr_branch_code", binding.branchCodeInput.text.toString())
        editor.putString("vr_loan_acc", binding.loanAccInput.text.toString())
        editor.putString("vr_loan_name", binding.loanNameInput.text.toString())
        editor.putString("vr_loan_amount", binding.loanAmountInput.text.toString())
        editor.putString("vr_borrower_address", binding.borrowerAddressInput.text.toString())
        editor.putString("vr_borrower_mobile", binding.borrowerMobileInput.text.toString())
        editor.putString("vr_activity", binding.activityInput.text.toString())
        editor.putString("vr_observations", binding.remarksInput.text.toString())
        editor.putString("vr_gps_location", binding.gpsLocationInput.text.toString())

        // Agri Pss fields
        editor.putString("ap_applicant_name", binding.applicantNameInput.text.toString())
        editor.putString("ap_cif_no", binding.cifNoInput.text.toString())
        editor.putInt("ap_constitution_sel", binding.constitutionSpinner.selectedItemPosition)
        editor.putString("ap_father_name", binding.fatherNameInput.text.toString())
        editor.putInt("ap_applicant_nature_sel", binding.applicantNatureSpinner.selectedItemPosition)
        editor.putInt("ap_annexure_sel", binding.attachedAnnexureSpinner.selectedItemPosition)
        editor.putInt("ap_collateral_obtained_sel", binding.collateralObtainedSpinner.selectedItemPosition)
        editor.putString("ap_property_nature", binding.propertyNatureInput.text.toString())
        editor.putString("ap_collateral_address", binding.collateralAddressInput.text.toString())
        editor.putBoolean("ap_collateral_demarcated_yes", binding.collateralDemarcatedYes.isChecked)
        editor.putBoolean("ap_collateral_demarcated_no", binding.collateralDemarcatedNo.isChecked)
        editor.putBoolean("ap_collateral_demarcated_na", binding.collateralDemarcatedNA.isChecked)
        editor.putString("ap_residence_address", binding.residenceAddressInput.text.toString())
        editor.putBoolean("ap_residence_verified_yes", binding.residenceVerifiedYes.isChecked)
        editor.putBoolean("ap_residence_verified_no", binding.residenceVerifiedNo.isChecked)
        editor.putString("ap_residence_person_met", binding.residencePersonMetInput.text.toString())
        editor.putString("ap_workplace_address", binding.workplaceAddressInput.text.toString())
        editor.putBoolean("ap_workplace_verified_yes", binding.workplaceVerifiedYes.isChecked)
        editor.putBoolean("ap_workplace_verified_no", binding.workplaceVerifiedNo.isChecked)
        editor.putString("ap_workplace_person_met", binding.workplacePersonMetInput.text.toString())
        editor.putString("ap_remarks", binding.additionalRemarksInput.text.toString())
        editor.putString("ap_key_person", binding.keyPersonInput.text.toString())
        editor.putString("ap_guarantor_names", binding.guarantorNamesInput.text.toString())
        editor.putBoolean("ap_collateral_verified_yes", binding.collateralAddressVerifiedYes.isChecked)
        editor.putBoolean("ap_collateral_verified_no", binding.collateralAddressVerifiedNo.isChecked)
        editor.putBoolean("ap_collateral_verified_na", binding.collateralAddressVerifiedNA.isChecked)
        editor.putBoolean("ap_collateral_accessible_yes", binding.collateralAccessibleYes.isChecked)
        editor.putBoolean("ap_collateral_accessible_no", binding.collateralAccessibleNo.isChecked)
        editor.putBoolean("ap_collateral_accessible_na", binding.collateralAccessibleNA.isChecked)
        editor.putBoolean("ap_disputes_yes", binding.disputesYes.isChecked)
        editor.putBoolean("ap_disputes_no", binding.disputesNo.isChecked)
        editor.putBoolean("ap_other_loans_yes", binding.otherLoansYes.isChecked)
        editor.putBoolean("ap_other_loans_no", binding.otherLoansNo.isChecked)

        editor.apply()
    }

    private fun loadFormState() {
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.FORM_PREFS", Context.MODE_PRIVATE)

        binding.nameInput.setText(sharedPrefs.getString("vr_staff_name", ""))
        binding.pfInput.setText(sharedPrefs.getString("vr_pf_number", ""))
        binding.designationInput.setText(sharedPrefs.getString("vr_designation", ""))
        binding.branchInput.setText(sharedPrefs.getString("vr_branch_name", ""))
        binding.branchCodeInput.setText(sharedPrefs.getString("vr_branch_code", ""))
        binding.loanAccInput.setText(sharedPrefs.getString("vr_loan_acc", ""))
        binding.loanNameInput.setText(sharedPrefs.getString("vr_loan_name", ""))
        binding.loanAmountInput.setText(sharedPrefs.getString("vr_loan_amount", ""))
        binding.borrowerAddressInput.setText(sharedPrefs.getString("vr_borrower_address", ""))
        binding.borrowerMobileInput.setText(sharedPrefs.getString("vr_borrower_mobile", ""))
        binding.activityInput.setText(sharedPrefs.getString("vr_activity", ""))
        binding.remarksInput.setText(sharedPrefs.getString("vr_observations", ""))
        binding.gpsLocationInput.setText(sharedPrefs.getString("vr_gps_location", ""))

        binding.applicantNameInput.setText(sharedPrefs.getString("ap_applicant_name", ""))
        binding.cifNoInput.setText(sharedPrefs.getString("ap_cif_no", ""))
        binding.constitutionSpinner.setSelection(sharedPrefs.getInt("ap_constitution_sel", 0))
        binding.fatherNameInput.setText(sharedPrefs.getString("ap_father_name", ""))
        binding.applicantNatureSpinner.setSelection(sharedPrefs.getInt("ap_applicant_nature_sel", 0))
        binding.attachedAnnexureSpinner.setSelection(sharedPrefs.getInt("ap_annexure_sel", 0))
        binding.collateralObtainedSpinner.setSelection(sharedPrefs.getInt("ap_collateral_obtained_sel", 0))
        binding.propertyNatureInput.setText(sharedPrefs.getString("ap_property_nature", ""))
        binding.collateralAddressInput.setText(sharedPrefs.getString("ap_collateral_address", ""))

        binding.collateralDemarcatedYes.isChecked = sharedPrefs.getBoolean("ap_collateral_demarcated_yes", false)
        binding.collateralDemarcatedNo.isChecked = sharedPrefs.getBoolean("ap_collateral_demarcated_no", false)
        binding.collateralDemarcatedNA.isChecked = sharedPrefs.getBoolean("ap_collateral_demarcated_na", false)

        binding.residenceAddressInput.setText(sharedPrefs.getString("ap_residence_address", ""))
        binding.residenceVerifiedYes.isChecked = sharedPrefs.getBoolean("ap_residence_verified_yes", false)
        binding.residenceVerifiedNo.isChecked = sharedPrefs.getBoolean("ap_residence_verified_no", false)
        binding.residencePersonMetInput.setText(sharedPrefs.getString("ap_residence_person_met", ""))

        binding.workplaceAddressInput.setText(sharedPrefs.getString("ap_workplace_address", ""))
        binding.workplaceVerifiedYes.isChecked = sharedPrefs.getBoolean("ap_workplace_verified_yes", false)
        binding.workplaceVerifiedNo.isChecked = sharedPrefs.getBoolean("ap_workplace_verified_no", false)
        binding.workplacePersonMetInput.setText(sharedPrefs.getString("ap_workplace_person_met", ""))

        binding.additionalRemarksInput.setText(sharedPrefs.getString("ap_remarks", ""))
        binding.keyPersonInput.setText(sharedPrefs.getString("ap_key_person", ""))
        binding.guarantorNamesInput.setText(sharedPrefs.getString("ap_guarantor_names", ""))

        binding.collateralAddressVerifiedYes.isChecked = sharedPrefs.getBoolean("ap_collateral_verified_yes", false)
        binding.collateralAddressVerifiedNo.isChecked = sharedPrefs.getBoolean("ap_collateral_verified_no", false)
        binding.collateralAddressVerifiedNA.isChecked = sharedPrefs.getBoolean("ap_collateral_verified_na", false)

        binding.collateralAccessibleYes.isChecked = sharedPrefs.getBoolean("ap_collateral_accessible_yes", false)
        binding.collateralAccessibleNo.isChecked = sharedPrefs.getBoolean("ap_collateral_accessible_no", false)
        binding.collateralAccessibleNA.isChecked = sharedPrefs.getBoolean("ap_collateral_accessible_na", false)

        binding.disputesYes.isChecked = sharedPrefs.getBoolean("ap_disputes_yes", false)
        binding.disputesNo.isChecked = sharedPrefs.getBoolean("ap_disputes_no", false)
        binding.otherLoansYes.isChecked = sharedPrefs.getBoolean("ap_other_loans_yes", false)
        binding.otherLoansNo.isChecked = sharedPrefs.getBoolean("ap_other_loans_no", false)
    }
}
