package com.accesspath.ui

import com.accesspath.domain.Availability
import com.accesspath.domain.Mobility
import com.accesspath.domain.ReportStatus
import com.accesspath.domain.Segment

object AppStrings {
    fun labelMobility(mobility: Mobility, lang: String): String = when (lang) {
        "hi" -> when (mobility) {
            Mobility.STANDARD -> "सामान्य (Standard)"
            Mobility.AVOID_STAIRS -> "सीढ़ियां छोड़ें (Avoid stairs)"
            Mobility.WHEELCHAIR -> "पहियाकुर्सी (Wheelchair)"
        }
        else -> mobility.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun labelSegment(segment: Segment, lang: String): String = when (lang) {
        "hi" -> when (segment) {
            Segment.CORRIDOR -> "गलियारा"
            Segment.OUTDOOR -> "बाहरी रास्ता"
            Segment.DOOR -> "दरवाज़ा"
            Segment.RAMP -> "रैंप"
            Segment.STAIRS -> "सीढ़ियां"
            Segment.ELEVATOR -> "लिफ्ट"
        }
        else -> segment.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun labelAvailability(availability: Availability, lang: String): String = when (lang) {
        "hi" -> when (availability) {
            Availability.AVAILABLE -> "उपलब्ध"
            Availability.UNAVAILABLE -> "अनुपलब्ध"
            Availability.UNKNOWN -> "अज्ञात"
        }
        else -> availability.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun labelReportStatus(status: ReportStatus, lang: String): String = when (lang) {
        "hi" -> when (status) {
            ReportStatus.PENDING -> "सत्यापन लंबित"
            ReportStatus.VERIFIED -> "सत्यापित बाधा"
            ReportStatus.REJECTED -> "अस्वीकृत"
            ReportStatus.RESOLVED -> "समाधानित"
        }
        else -> status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun t(key: String, lang: String): String {
        val hi = mapOf(
            "nav_tab" to "नेविगेट",
            "reports_tab" to "रिपोर्ट्स",
            "manage_tab" to "प्रबंधन",
            "settings_tab" to "सेटिंग्स",
            "tagline" to "कैंपस का सुलभ व सुगम रास्ता",
            "help" to "सहायता",
            "preparing" to "कैंपस मैप तैयार हो रहा है...",
            "nav_eyebrow" to "आपका कैंपस साथी",
            "nav_title" to "रास्ता खोजें",
            "nav_detail" to "आपकी गतिशीलता प्राथमिकताओं का सम्मान करने वाले सुगम मार्ग।",
            "from" to "शुरुआत:",
            "to" to "मंज़िल:",
            "plan_route" to "रूट प्लान करें",
            "qr_scan" to "QR कोड स्कैन करें",
            "campus_overview" to "कैंपस का नक्शा",
            "map_legend" to "हरा: मार्ग · नारंगी: सक्रिय कदम · लाल डैश: प्रतिबंधित · धूसर: अन्य रास्ते\nज़ूम करने के लिए पिंच करें, ड्रैग करें।",
            "reached_destination" to "आप पहले से ही अपनी मंज़िल पर हैं",
            "accessible_journey" to "आपका सुगम सफर",
            "distance" to "मार्ग दूरी",
            "score" to "रूट स्कोर (दूरी व बाधा भार)",
            "last_confirmed" to "पुष्ट किया गया स्थान:",
            "set_current" to "वर्तमान स्थान सेट करें",
            "i_reached" to "मैं यहां पहुंच गया हूं",
            "speak_directions" to "🔊 आवाज़ में सुनें",
            "stop_speech" to "⏹ आवाज़ बंद करें",
            "quick_destinations" to "त्वरित स्थान (Quick Favourites):",
            "start_guided_nav" to "🧭 मार्गदर्शन शुरू करें",
            "stop_guided_nav" to "⏹ मार्गदर्शन समाप्त करें",
            "next_step" to "अगला कदम ▶",
            "prev_step" to "◀ पिछला कदम",
            "no_route" to "कोई सुगम मार्ग उपलब्ध नहीं है",
            "no_route_sub" to "आपकी सुरक्षा बाधाओं के कारण कोई मान्य रास्ता नहीं मिला। स्थान की पुष्टि करें या लिफ्ट स्थिति देखें।",
            "reports_eyebrow" to "कैंपस सुरक्षा",
            "reports_title" to "रास्ते खुले रखें",
            "reports_detail" to "रुकावटों की रिपोर्ट दर्ज करें और स्थिति ट्रैक करें।",
            "report_barrier" to "बाधा की रिपोर्ट करें",
            "all" to "सभी",
            "no_reports" to "फिलहाल कोई रिपोर्ट नहीं है",
            "review_report" to "इस रिपोर्ट की समीक्षा करें",
            "manage_eyebrow" to "कैंपस संचालन",
            "manage_title" to "कैंपस प्रबंधन",
            "manage_detail" to "सुविधाओं की स्थिति की जांच करें और आपातकालीन सिमुलेशन चलाएं।",
            "demo_role" to "लोकल समीक्षक मोड",
            "map_editor" to "मैप एडिटर (Landmark & Connector Editor)",
            "add_landmark" to "+ नया लैंडमार्क / कमरा जोड़ें",
            "add_connector" to "+ नया रास्ता / कॉरिडोर जोड़ें",
            "simulate_lifts_down" to "सभी लिफ्ट बंद होने का सिमुलेशन चलाएं",
            "restore_lifts" to "सभी लिफ्ट पुनः चालू करें",
            "block_path" to "एक रास्ता अवरुद्ध करें",
            "reset_demo" to "डेमो डेटा रीसेट करें",
            "settings_eyebrow" to "ऐप जानकारी",
            "settings_title" to "सरल और सुरक्षित",
            "settings_detail" to "ऑफ़लाइन नेविगेशन। कोई डेटा संग्रह नहीं। खाता अनावश्यक।",
            "language" to "भाषा / Language",
            "import_map" to "कैंपस JSON मैप आयात (Import) करें",
            "export_map" to "कैंपस JSON मैप निर्यात (Export) करें"
        )
        return if (lang == "hi") hi[key] ?: key else when(key) {
            "nav_tab" -> "Navigate"
            "reports_tab" -> "Reports"
            "manage_tab" -> "Manage"
            "settings_tab" -> "Settings"
            "tagline" -> "A clearer way through campus"
            "help" -> "Help"
            "preparing" -> "Preparing your campus map"
            "nav_eyebrow" -> "Your campus companion"
            "nav_title" -> "Find your way."
            "nav_detail" -> "Routes that respect your mobility needs."
            "from" -> "From"
            "to" -> "To"
            "plan_route" -> "Plan a route"
            "qr_scan" -> "Scan QR placard"
            "campus_overview" -> "Campus overview"
            "map_legend" -> "Green: route · Amber: current step · Red dashed: restricted · Grey: other paths\nPinch to zoom, drag to pan. Directions below provide a text alternative."
            "reached_destination" -> "You are already at the destination"
            "accessible_journey" -> "Your accessible journey"
            "distance" -> "Route distance"
            "score" -> "Route score, not minutes"
            "last_confirmed" -> "Last confirmed position:"
            "set_current" -> "Set current location"
            "i_reached" -> "I have reached"
            "speak_directions" -> "🔊 Speak directions"
            "stop_speech" -> "⏹ Stop voice"
            "quick_destinations" -> "Quick destinations:"
            "start_guided_nav" -> "🧭 Start guided navigation"
            "stop_guided_nav" -> "⏹ Exit guidance"
            "next_step" -> "Next step ▶"
            "prev_step" -> "◀ Previous step"
            "no_route" -> "No accessible route available"
            "no_route_sub" -> "Your requirements have not been relaxed. Confirm your location, choose another destination or review facility status."
            "reports_eyebrow" -> "Community care"
            "reports_title" -> "Keep paths open."
            "reports_detail" -> "Report barriers and follow their verification status."
            "report_barrier" -> "Report a barrier"
            "all" -> "All"
            "no_reports" -> "No reports here"
            "review_report" -> "Review this report"
            "manage_eyebrow" -> "Campus operations"
            "manage_title" -> "Stay one step ahead."
            "manage_detail" -> "Review facility conditions and demonstrate changing routes."
            "demo_role" -> "Local demo role"
            "map_editor" -> "Map Editor (Landmark & Connector Editor)"
            "add_landmark" -> "+ Add Landmark / Room"
            "add_connector" -> "+ Add Path / Corridor"
            "simulate_lifts_down" -> "Simulate all lifts unavailable"
            "restore_lifts" -> "Restore all lifts"
            "block_path" -> "Block a path"
            "reset_demo" -> "Reset demo dataset"
            "settings_eyebrow" -> "About this build"
            "settings_title" -> "Simple by design."
            "settings_detail" -> "Offline navigation. Clear limitations. No account needed."
            "language" -> "Language"
            "import_map" -> "Import campus JSON map"
            "export_map" -> "Export campus JSON map"
            else -> key
        }
    }
}
