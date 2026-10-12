# ShadowVision3 — محرّر صور يعمل دون اتصال

مشروع Android أصلي بلغة Kotlin وواجهات Jetpack Compose. يقدّم أداة محلية لإخفاء أجزاء من الصور عبر الموزاييك أو التمويه أو التعتيم، مع اختيار الصورة والرسم على منطقة التحديد ومعاينة النتيجة وحفظها في المعرض.

> **حدود المنتج:** هذا المشروع لا يزيل الملابس، ولا يعيد بناء الأجسام أو البشرة، ولا يولّد محتوى جديداً. أدوات المعالجة فيه تحجب المنطقة المحددة فقط.

## الخصائص

- اختيار الصور باستخدام منتقي الصور النظامي؛ لا يطلب التطبيق صلاحية قراءة مكتبة الصور كاملة.
- رسم حر فوق الصورة مع تراجع ومسح للتحديد والتحكم بحجم الفرشاة.
- ثلاثة تأثيرات محلية: موزاييك، تمويه، وتعتيم بلون ثابت.
- حفظ النتيجة إلى `Pictures/ShadowVision` عبر `MediaStore`.
- لا توجد صلاحية `INTERNET` أو خدمة خلفية؛ معالجة الصورة لا تحتاج إلى شبكة.
- تصغير الصور الكبيرة عند فتحها لتقليل استهلاك الذاكرة على الهاتف.

## البناء

افتح مجلد المشروع في Android Studio مع JDK 17 وAndroid SDK Platform 35، ثم نفّذ Gradle Sync وشغّل إعداد `app`. أضفت Gradle Wrapper (`gradlew` و`gradlew.bat` مع ملفات `gradle/wrapper`)؛ يمكن البناء من الطرفية باستخدام `./gradlew :app:assembleDebug`. إذا لم يكتشف Android Studio مسار SDK تلقائياً، انسخ `local.properties.example` إلى `local.properties` وعدّل `sdk.dir` إلى مسار SDK المحلي؛ ملف `local.properties` مستثنى من Git. يستخدم المشروع Gradle 8.9 وAndroid Gradle Plugin 8.7.3 وKotlin 2.0.21.

## ملاحظات التنفيذ

- نقطة الدخول: `app/src/main/java/ai/shadowvision/cleanup/MainActivity.kt`
- واجهة Compose: `EditorScreen.kt` و`EditorCanvas.kt`
- إنشاء قناع الفرشاة والتأثيرات: `BitmapRedactor.kt`
- اختيار الصور ومعالجتها وحفظها: `PhotoEditorViewModel.kt`
- كل التأثيرات حتمية وغير توليدية؛ لا يتضمن المشروع تنزيل نماذج أو تشغيل نموذج Inpainting.
