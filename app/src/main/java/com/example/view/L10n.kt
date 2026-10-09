package com.example.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Current app language code: "en", "ne" (Nepali) or "hi" (Hindi). Provided from MainActivity. */
val LocalLang = compositionLocalOf { "en" }

/** Space reserved at the bottom of scrolling screens so the floating nav bar never covers content. */
val LocalNavBarInset = compositionLocalOf { 0.dp }

object Languages {
    val all = listOf(
        Triple("en", "English", "English"),
        Triple("ne", "नेपाली", "Nepali"),
        Triple("hi", "हिन्दी", "Hindi")
    )
}

/**
 * Tiny translation table: key → [English, Nepali, Hindi].
 * Add a row here, then call tr("key") wherever the text is shown.
 */
object L10n {
    private val t: Map<String, Array<String>> = mapOf(
        // navigation
        "nav_Games" to arrayOf("Games", "खेलहरू", "गेम्स"),
        "nav_Apps" to arrayOf("Apps", "एपहरू", "ऐप्स"),
        "nav_Library" to arrayOf("My Library", "मेरो पुस्तकालय", "मेरी लाइब्रेरी"),
        "nav_Console" to arrayOf("Admin", "एडमिन", "एडमिन"),
        "nav_Chat" to arrayOf("Chat", "च्याट", "चैट"),
        "nav_Profile" to arrayOf("Profile", "प्रोफाइल", "प्रोफ़ाइल"),
        // profile header
        "dev_console" to arrayOf("Developer Console", "डेभलपर कन्सोल", "डेवलपर कंसोल"),
        "dev_console_sub" to arrayOf("Verified publisher registry & tracking console", "प्रमाणित प्रकाशक रेजिस्ट्री र ट्र्याकिङ कन्सोल", "सत्यापित प्रकाशक रजिस्ट्री और ट्रैकिंग कंसोल"),
        // chat
        "chat_messages" to arrayOf("Messages", "सन्देशहरू", "संदेश"),
        "chat_inbox" to arrayOf("Inbox", "इनबक्स", "इनबॉक्स"),
        "chat_developers" to arrayOf("Developers", "डेभलपरहरू", "डेवलपर्स"),
        "chat_hint" to arrayOf("Message…", "सन्देश…", "संदेश…"),
        "notif_title" to arrayOf("Notifications", "सूचनाहरू", "सूचनाएं"),
        "notif_caught_up" to arrayOf("You're all caught up", "तपाईंले सबै हेरिसक्नुभयो", "आप सब देख चुके हैं"),
        "notif_mark_all" to arrayOf("Mark all as read", "सबै पढिसकेको चिनो लगाउनुहोस्", "सभी को पढ़ा हुआ चिह्नित करें"),
        "notif_empty" to arrayOf("No notifications yet", "अहिलेसम्म कुनै सूचना छैन", "अभी कोई सूचना नहीं है"),
        "notif_empty_sub" to arrayOf("Announcements and alerts from Dark Store will show up here.", "डार्क स्टोरका घोषणा र अलर्टहरू यहाँ देखिनेछन्।", "डार्क स्टोर की घोषणाएं और अलर्ट यहां दिखेंगे।"),
        // home
        "tagline" to arrayOf("Safe · Fast · Independent", "सुरक्षित · छिटो · स्वतन्त्र", "सुरक्षित · तेज़ · स्वतंत्र"),
        "search_hint" to arrayOf("Search apps & games…", "एप र गेम खोज्नुहोस्…", "ऐप्स और गेम्स खोजें…"),
        "sec_recommended" to arrayOf("Recommended For You", "तपाईंका लागि सिफारिस", "आपके लिए सुझाव"),
        "sec_popular" to arrayOf("Most Popular", "सबैभन्दा लोकप्रिय", "सबसे लोकप्रिय"),
        "sec_free" to arrayOf("Top Free", "शीर्ष निःशुल्क", "टॉप फ्री"),
        "sec_all" to arrayOf("All apps", "सबै एपहरू", "सभी ऐप्स"),
        "more" to arrayOf("More", "थप", "और"),
        "voice_prompt" to arrayOf("Search apps & games", "एप र गेम खोज्नुहोस्", "ऐप्स और गेम्स खोजें"),
        "voice_missing" to arrayOf("Voice search isn't available on this device", "यो डिभाइसमा भ्वाइस खोज उपलब्ध छैन", "इस डिवाइस पर वॉइस सर्च उपलब्ध नहीं है"),
        // app details
        "det_verified" to arrayOf("Verified by Dark Store", "डार्क स्टोरद्वारा प्रमाणित", "डार्क स्टोर द्वारा सत्यापित"),
        "det_verified_sub" to arrayOf("Reviewed and approved by Dark Store admins", "डार्क स्टोर एडमिनले समीक्षा गरी स्वीकृत गरेको", "डार्क स्टोर एडमिन द्वारा समीक्षा और स्वीकृत"),
        "det_about" to arrayOf("About this app", "यो एपको बारेमा", "इस ऐप के बारे में"),
        "det_whatsnew" to arrayOf("What's New", "नयाँ के छ", "नया क्या है"),
        "det_less" to arrayOf("Less", "कम", "कम"),
        "det_size" to arrayOf("Size", "साइज", "आकार"),
        "det_ads" to arrayOf("Ads", "विज्ञापन", "विज्ञापन"),
        "det_ads_yes" to arrayOf("Contains ads", "विज्ञापन छन्", "विज्ञापन हैं"),
        "det_ads_no" to arrayOf("None", "छैन", "नहीं"),
        "det_reviews" to arrayOf("Reviews", "समीक्षा", "समीक्षाएं"),
        "det_open_ext" to arrayOf("Open externally", "बाहिर खोल्नुहोस्", "बाहर खोलें"),
        // collections
        "col_title" to arrayOf("Collections", "संग्रहहरू", "संग्रह"),
        "col_apps" to arrayOf("%d apps", "%d एपहरू", "%d ऐप्स"),
        "col_empty" to arrayOf("No apps in this collection yet.", "यो संग्रहमा अहिले कुनै एप छैन।", "इस संग्रह में अभी कोई ऐप नहीं है।"),
        "col_view" to arrayOf("View", "हेर्नुहोस्", "देखें"),
        // settings
        "set_title" to arrayOf("Settings", "सेटिङ", "सेटिंग्स"),
        "set_sub" to arrayOf("Make Dark Store work your way", "डार्क स्टोरलाई आफ्नै शैलीमा चलाउनुहोस्", "डार्क स्टोर को अपने तरीके से चलाएं"),
        "set_guest" to arrayOf("Guest", "अतिथि", "अतिथि"),
        "set_account" to arrayOf("Your account", "तपाईंको खाता", "आपका खाता"),
        "set_signin_hint" to arrayOf("Sign in from the Profile tab", "प्रोफाइल ट्याबबाट साइन इन गर्नुहोस्", "प्रोफ़ाइल टैब से साइन इन करें"),
        "g_language" to arrayOf("Language", "भाषा", "भाषा"),
        "g_appearance" to arrayOf("Appearance", "रूपरङ", "रूप-रंग"),
        "g_downloads" to arrayOf("Downloads & storage", "डाउनलोड र भण्डारण", "डाउनलोड और स्टोरेज"),
        "g_notifications" to arrayOf("Notifications", "सूचनाहरू", "सूचनाएं"),
        "g_system" to arrayOf("System", "प्रणाली", "सिस्टम"),
        "g_about" to arrayOf("About", "बारेमा", "जानकारी"),
        "lang_note" to arrayOf("Menus, Settings and Collections are translated. More screens are being added.", "मेनु, सेटिङ र संग्रहहरू अनुवाद गरिएका छन्। अरू स्क्रिन थपिँदैछन्।", "मेनू, सेटिंग्स और संग्रह का अनुवाद हो चुका है। और स्क्रीन जोड़ी जा रही हैं।"),
        "light" to arrayOf("Light", "उज्यालो", "लाइट"),
        "dark" to arrayOf("Dark", "अँध्यारो", "डार्क"),
        "oled_t" to arrayOf("Pure OLED black", "शुद्ध OLED कालो", "प्योर OLED ब्लैक"),
        "oled_s" to arrayOf("Deepest blacks — saves battery on AMOLED screens", "सबैभन्दा गाढा कालो — AMOLED स्क्रिनमा ब्याट्री बचत", "सबसे गहरा काला — AMOLED स्क्रीन पर बैटरी बचत"),
        "wifi_t" to arrayOf("Wi‑Fi only downloads", "वाइफाइमा मात्र डाउनलोड", "केवल वाई‑फाई पर डाउनलोड"),
        "wifi_s" to arrayOf("Pause downloads on mobile data", "मोबाइल डाटामा डाउनलोड रोक्नुहोस्", "मोबाइल डेटा पर डाउनलोड रोकें"),
        "auto_t" to arrayOf("Auto-start installation", "स्वतः स्थापना सुरु", "ऑटो इंस्टॉल शुरू"),
        "auto_s" to arrayOf("Open the installer as soon as a download finishes", "डाउनलोड सकिना साथ इन्स्टलर खोल्नुहोस्", "डाउनलोड पूरा होते ही इंस्टॉलर खोलें"),
        "cache_t" to arrayOf("Downloaded APK cache", "डाउनलोड गरिएको APK क्यास", "डाउनलोड की गई APK कैश"),
        "cache_s" to arrayOf("Using %s", "%s प्रयोग भइरहेको छ", "%s उपयोग में है"),
        "clear" to arrayOf("CLEAR", "खाली गर्नुहोस्", "साफ़ करें"),
        "cleared" to arrayOf("APK cache cleared", "APK क्यास खाली भयो", "APK कैश साफ़ हो गई"),
        "nothing_clear" to arrayOf("Nothing to clear", "खाली गर्न केही छैन", "साफ़ करने के लिए कुछ नहीं"),
        "blocked_t" to arrayOf("Notifications are blocked", "सूचनाहरू रोकिएका छन्", "सूचनाएं ब्लॉक हैं"),
        "blocked_s" to arrayOf("Turn them on in system settings to receive alerts", "अलर्ट पाउन सिस्टम सेटिङमा खोल्नुहोस्", "अलर्ट पाने के लिए सिस्टम सेटिंग्स में चालू करें"),
        "open" to arrayOf("OPEN", "खोल्नुहोस्", "खोलें"),
        "n_new" to arrayOf("New apps", "नयाँ एपहरू", "नए ऐप्स"),
        "n_new_s" to arrayOf("When a new app is published", "नयाँ एप प्रकाशित हुँदा", "जब कोई नया ऐप प्रकाशित हो"),
        "n_upd" to arrayOf("App updates", "एप अपडेटहरू", "ऐप अपडेट"),
        "n_upd_s" to arrayOf("When an app you installed has an update", "तपाईंले इन्स्टल गरेको एपमा अपडेट आउँदा", "जब आपके इंस्टॉल किए ऐप का अपडेट आए"),
        "n_ann" to arrayOf("Announcements", "घोषणाहरू", "घोषणाएं"),
        "n_ann_s" to arrayOf("News and messages from the Dark Store team", "डार्क स्टोर टोलीबाट समाचार र सन्देश", "डार्क स्टोर टीम की खबरें और संदेश"),
        "n_sub" to arrayOf("Submission status", "सबमिसनको स्थिति", "सबमिशन स्थिति"),
        "n_sub_s" to arrayOf("App review results and new submissions", "एप समीक्षाको नतिजा र नयाँ सबमिसन", "ऐप समीक्षा परिणाम और नए सबमिशन"),
        "n_chat" to arrayOf("Chat messages", "च्याट सन्देशहरू", "चैट संदेश"),
        "n_chat_s" to arrayOf("When someone sends you a message", "कसैले सन्देश पठाउँदा", "जब कोई संदेश भेजे"),
        "sys_bg" to arrayOf("Background activity", "पृष्ठभूमि गतिविधि", "बैकग्राउंड गतिविधि"),
        "sys_bg_s" to arrayOf("Keep downloads and installs running in the background", "डाउनलोड र इन्स्टल पृष्ठभूमिमा चलिरहोस्", "डाउनलोड और इंस्टॉल बैकग्राउंड में चलते रहें"),
        "enabled" to arrayOf("ENABLED", "सक्रिय", "चालू"),
        "active" to arrayOf("ACTIVE", "सक्रिय", "सक्रिय"),
        "activate" to arrayOf("ACTIVATE", "सक्रिय गर्नुहोस्", "सक्रिय करें"),
        "sys_admin" to arrayOf("Device administrator", "डिभाइस प्रशासक", "डिवाइस एडमिनिस्ट्रेटर"),
        "sys_admin_s" to arrayOf("Lets Dark Store uninstall apps for you", "डार्क स्टोरलाई एप हटाउन दिन्छ", "डार्क स्टोर को ऐप हटाने देता है"),
        "sys_db" to arrayOf("Offline database", "अफलाइन डाटाबेस", "ऑफ़लाइन डेटाबेस"),
        "sys_db_s" to arrayOf("Your catalog is cached on this device", "तपाईंको सूची यो डिभाइसमा सुरक्षित छ", "आपकी सूची इस डिवाइस पर सहेजी है"),
        "version" to arrayOf("Version", "संस्करण", "संस्करण"),
        "about_desc" to arrayOf(
            "A high-performance app marketplace with verified developers, live chat, targeted push notifications, home-screen widgets and over-the-air updates.",
            "प्रमाणित डेभलपर, लाइभ च्याट, लक्षित पुश सूचना, होम-स्क्रिन विजेट र अपडेट सहितको उच्च-गतिको एप बजार।",
            "सत्यापित डेवलपर्स, लाइव चैट, लक्षित पुश नोटिफिकेशन, होम-स्क्रीन विजेट और ओवर-द-एयर अपडेट वाला तेज़ ऐप मार्केटप्लेस।"
        ),
        "legal_t" to arrayOf("Legal & policies", "कानुनी र नीतिहरू", "कानूनी और नीतियां"),
        "legal_s" to arrayOf("Terms, privacy and developer ecosystem rules", "सर्त, गोपनीयता र डेभलपर नियमहरू", "शर्तें, गोपनीयता और डेवलपर नियम")
    )

    fun get(key: String, lang: String): String {
        val row = t[key] ?: return key
        val idx = when (lang) { "ne" -> 1; "hi" -> 2; else -> 0 }
        return row.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: row[0]
    }
}

@Composable
fun tr(key: String): String = L10n.get(key, LocalLang.current)

@Composable
fun tr(key: String, vararg args: Any): String = String.format(L10n.get(key, LocalLang.current), *args)
