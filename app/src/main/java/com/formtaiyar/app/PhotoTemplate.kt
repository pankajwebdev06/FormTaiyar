package com.formtaiyar.app

/**
 * Data class representing a photo template with exact 2026 government specs
 */
data class PhotoTemplate(
    val id: String,
    val nameHindi: String,
    val nameEnglish: String,
    val widthPx: Int,
    val heightPx: Int,
    val maxSizeKB: Int,
    val dimensionLabel: String,
    val iconResId: Int,
    val cardColorRes: Int
)

object Templates {

    val PAN_CARD = PhotoTemplate(
        id = "pan",
        nameHindi = "PAN Card Photo",
        nameEnglish = "PAN Card",
        widthPx = 213,
        heightPx = 213,
        maxSizeKB = 300,
        dimensionLabel = "35×25mm • Max 300KB",
        iconResId = R.drawable.ic_pan,
        cardColorRes = R.color.card_pan
    )

    val AADHAAR_PASSPORT = PhotoTemplate(
        id = "aadhaar_passport",
        nameHindi = "Aadhaar / Passport Photo",
        nameEnglish = "Aadhaar & Passport",
        widthPx = 630,
        heightPx = 810,
        maxSizeKB = 100,
        dimensionLabel = "35×45mm • Max 100KB",
        iconResId = R.drawable.ic_aadhaar,
        cardColorRes = R.color.card_aadhaar
    )

    val STATE_EXAM = PhotoTemplate(
        id = "ssc",
        nameHindi = "SSC / Sarkari Exam Photo",
        nameEnglish = "State Govt Exams",
        widthPx = 472,
        heightPx = 378,
        maxSizeKB = 50,
        dimensionLabel = "45×35mm • Max 50KB",
        iconResId = R.drawable.ic_exam,
        cardColorRes = R.color.card_exam
    )

    val CUSTOM = PhotoTemplate(
        id = "custom",
        nameHindi = "Custom Resize",
        nameEnglish = "Custom Size",
        widthPx = 0,
        heightPx = 0,
        maxSizeKB = 500,
        dimensionLabel = "Apni size choose karein",
        iconResId = R.drawable.ic_custom,
        cardColorRes = R.color.card_custom
    )

    val all = listOf(PAN_CARD, AADHAAR_PASSPORT, STATE_EXAM, CUSTOM)
}
