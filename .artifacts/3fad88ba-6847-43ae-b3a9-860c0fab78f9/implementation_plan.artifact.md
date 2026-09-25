# خطة تنفيذ ميزة النسخ الاحتياطي واستعادة البيانات (SQLite Backup & Restore)

حماية كافة بيانات المتجر (المنتجات، المبيعات، العملاء، المشتريات، المرتجعات، والدفعات) بآلية نسخ آمنة لملف قاعدة البيانات `mystore.db` وإمكانية استعادتها الكاملة في أي وقت.

## User Review Required

> [!IMPORTANT]
> **كيف تعمل الميزة بذكاء وأمان:**
> 1. **إنشاء نسخة احتياطية**:
>    - يتم نسخ ملف قاعدة البيانات الفعلي `mystore.db` بالكامل من المسار الداخلي للتطبيق إلى مجلد معروف وعلني ومستقل في ذاكرة الهاتف الخارجية (`Documents/MyStoreBackups/` أو `Downloads/MyStoreBackups/`).
>    - يتم إغلاق وقفل الكتابات على قاعدة البيانات مؤقتاً أثناء النسخ لضمان سلامة الملف وعدم تلف أي جدول.
> 2. **استعادة النسخة الاحتياطية**:
>    - يتم إغلاق اتصالات قاعدة البيانات، ثم استبدال ملف `mystore.db` الحالي بالنسخة الاحتياطية المحفوظة في المجلد المعروف.
>    - إعادة تشغيل الواجهات تلقائياً لتظهر كل البيانات السابقة (المنتجات والديون والمبيعات) فوراً من حيث توقفت.

## Proposed Changes

### [وحدة النسخ الاحتياطي والاستعادة]
- **[NEW]** [activity_backup.xml](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2/app/src/main/res/layout/activity_backup.xml): واجهة مستقلة وأنيقة لإنشاء واستعادة النسخة الاحتياطية مع عرض اسم المجلد وتاريخ آخر نسخة.
- **[NEW]** [BackupActivity.java](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2/app/src/main/java/com/lap1/mystore/activities/BackupActivity.java): نشاط جافا يتولى قراءة ونسخ واختبار وتأكيد سلامة ملف `mystore.db` عند النسخ والاستعادة.
- **[MODIFY]** [activity_dashboard.xml](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2/app/src/main/res/layout/activity_dashboard.xml) & [DashboardActivity.java](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2/app/src/main/java/com/lap1/mystore/activities/DashboardActivity.java): إضافة زر اختصار لوحدة "النسخ الاحتياطي والاستعادة".
- **[MODIFY]** [AndroidManifest.xml](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2/app/src/main/AndroidManifest.xml): تسجيل `BackupActivity` وصلاحيات الوصول للتخزين.

## Verification Plan

### Automated Tests
- بناء المشروع والتأكد من نجاح الـ Build (`app:assembleDebug`).

### Manual Verification
- إنشاء نسخة احتياطية من الشاشة الجديدة، التأكد من ظهور ملف `mystore_backup.db` في مجلد التخزين، ثم إضافة منتج أو فاتورة جديدة وعمل "استعادة نسخة احتياطية" للتأكد من عودة قاعدة البيانات بالضبط كما كانت.
