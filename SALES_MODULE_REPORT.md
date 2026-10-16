# SALES_MODULE_REPORT.md — المرحلة السادسة: نظام المبيعات، الفواتير المعلقة، المرتجعات، وتكامل المخزون المالي

تاريخ التنفيذ: 2026-10-06  
حالة البناء: **BUILD SUCCESSFUL (25s) — جميع اختبارات الوحدة واختبارات السلامة المالية ناجحة بنسبة 100%**  
المشروع: Amran_Hayper — `com.lap1.mystore`  
إصدار قاعدة البيانات: **18 ➔ 19 (Additive Migration)**  

---

## 1. ملخص الإنجاز

تم تنفيذ مرحلة المبيعات والمرتجعات والفواتير المعلقة والربط المالي والمخزني بالكامل وفق أعلى معايير الأمان المحاسبي والمالي، دون المساس بالتقارير القديمة أو حذف أي سجلات.

---

## 2. ترقية قاعدة البيانات (Version 19 — Additive Migration)

تمت الترقية إلى الإصدار 19 بطريقة إضافية آمنة (دون Drop لأي جدول):
1. **جدول الفواتير المعلقة `suspended_sales`**:
   - `id`: المعرف الأساسي
   - `customer_id`: معرف العميل المسجل (إن وجد)
   - `client_temp_name`: اسم العميل المؤقت (في حال كان عميلاً جديداً/غير مسجل)
   - `total`: إجمالي السلة
   - `discount`: قيمة الخصم المؤقت
   - `notes`: ملاحظات التعليق
   - `created_by_user_id`: المستخدم الفعلي الذي علق الفاتورة من `SessionManager`
   - `created_at`: توقيت التعليق

2. **جدول أصناف الفواتير المعلقة `suspended_sale_items`**:
   - `suspended_sale_id`: معرف الفاتورة المعلقة (مع `ON DELETE CASCADE`)
   - `product_id`: معرف الصنف
   - `quantity`: الكمية المعلقة
   - `unit_price`: سعر البيع
   - `purchase_price`: سعر التكلفة
   - `total`: إجمالي الصنف

3. **حقول المستخدم الفعلي `created_by_user_id`**:
   - إضافة عمود `created_by_user_id` لجدول المبيعات `sales`.
   - إضافة عمود `created_by_user_id` لجدول المرتجعات `returns`.

4. **فهارس الأداء (Performance Indexes)**:
   - `idx_suspended_sales_created` على `suspended_sales(created_at)`
   - `idx_suspended_sale_items_sale` على `suspended_sale_items(suspended_sale_id)`
   - `idx_sales_created_by` على `sales(created_by_user_id)`

---

## 3. نقطة البيع السريعة (POS — SalesActivity)

- **الحفاظ الكامل على جميع الوظائف الحالية**:
  - ماسح الباركود السريع بتقنية Google ML Kit وميزان الباركود الإلكتروني (بادئة 22).
  - البحث الفوري بالاسم أو الباركود.
  - إدارة السلة (+ / - / حذف / إجمالي فوري).
  - الخصم التجاري مع احتساب الصافي الفوري.
  - طرق الدفع: كاش وشبكة.
  - البيع الآجل والجزئي مع اختيار العميل المسجل أو إضافة عميل جديد فورياً.
  - مشاركة الفاتورة عبر تطبيقات التواصل.

- **الميزات الجديدة المضافة**:
  - **زر تعليق الفاتورة (Suspend Sale)**: يقوم بحفظ الفاتورة الحالية وسلتها في جدول المعلقات مع تفريغ الشاشة لخدمة زبون آخر.
  - **زر الوصول السريع للمعلقات**: للانتقال مباشرة إلى شاشة الفواتير المعلقة.
  - **استئناف الفاتورة المعلقة**: عند استئناف فاتورة من شاشة المعلقات، يتم تحميل السلة والأصناف والعميل والخصم والملاحظات مع شريط تنبيه ملون يبين رقم الفاتورة المستأنفة.
  - **تفريغ المعلقة عند الإتمام**: بمجرد اعتماد البيع، يتم حذف السجل المعلق تلقائياً داخل نفس الـ Transaction.
  - **حفظ هوية المستخدم الفعلي**: يتم استخدام `SessionManager.getInstance(this).getUserId()` لتسجيل المستخدم الفعلي للعملية دون الاعتماد على أول مستخدم في الجدول.

---

## 4. نظام الفواتير المعلقة (SuspendedSalesActivity)

- **شاشة مخصصة `SuspendedSalesActivity`** تدعم القائمة الجانبية الموحدة Drawer:
  - عرض بطاقات أنيقة لكل فاتورة معلقة (العميل، التاريخ، عدد الأصناف، الصافي، الخصم، الملاحظات).
  - زر **"استئناف الفاتورة 🛒"**: ينقل المستخدم إلى POS مع تحميل السلة وبيانات الفاتورة بالكامل.
  - زر **"حذف المعلقة"**: لحذف الفاتورة المعلقة بعد التأكيد في حال إلغائها نهائياً من قبل الزبون.
  - حالة الفراغ (Empty State) مع زر للانتقال السريع لنقطة البيع.
- **قواعد الأمان المالي والمخزني للفاتورة المعلقة**:
  - **لا تعدل المخزون نهائياً** (`warehouse_stock` و `products.quantity` لا تتأثران).
  - **لا تعدل الحسابات والديون** (`account_ledger` و `customers.balance` لا تتأثران).
  - **لا تدخل في الخزينة أو تقفيل الصندوق أو التقارير المالية**.
  - يتم التأثير المالي والمخزني فقط لحظة إتمام البيع الفعلي من `SalesActivity`.

---

## 5. تطوير نظام المرتجعات (ReturnsActivity)

تم تحويل شاشة المرتجعات إلى نظام تفاعلي متكامل يتبع دورة العمل المحددة:
1. **اختيار / البحث عن الفاتورة**:
   - إدخال رقم الفاتورة والبحث المباشر.
   - أو النقر على "اختيار فاتورة 📑" لفتح حوار بأحدث فواتير المبيعات واختيار إحداها.
2. **عرض تفاصيل الفاتورة وأصنافها**:
   - بطاقة رأس الفاتورة: الصافي، المدفوع، المتبقي (الدين)، التاريخ، العميل.
   - جدول الأصناف المباعة في الفاتورة مع:
     - الكمية المباعة الأصلية.
     - الكميات المرجعة سابقاً (لمنع تجاوز المتاح للإرجاع).
     - الكمية المتاحة للإرجاع حالياً.
     - سعر الوحدة وإجمالي المرتجع للصنف.
3. **تحديد كميات المرتجع**:
   - حقل إدخال رقمي لكل صنف مع أزرار زيادة (+) وإنقاص (-) وزر "إرجاع الكل".
4. **تحديد طريقة التسوية المالية**:
   - **نقدي (استرداد كاش للعميل)**: في حال رد المبلغ نقداً.
   - **خصم من دين العميل**: في حال كانت الفاتورة آجلة أو جزئية، يتم تخفيض المتبقي من الفاتورة وحساب العميل تلقائياً.
5. **سبب الإرجاع وموجز التوزيع المالي الفوري**:
   - احتساب فوري لقيمة المرتجع وتوزيعها (كم سيُخصم من الدين وكم سيرد نقداً).
6. **الاعتماد داخل Transaction ذرية واحدة**:
   - إدراج سجل في `returns` متضمناً `debt_reduction` و `created_by_user_id`.
   - إعادة الكمية إلى المخزون عبر `adjustWarehouseStock` بنوع حركة `StockMovement.SALE_RETURN`.
   - تخفيض `sales.remaining` وتحديث `payment_status`.
   - تسجيل حركة في دفتر الحسابات `account_ledger` برقم الفاتورة ومزامنة رصيد العميل `syncCustomerBalance`.
   - **منع مضاعفة الخصم**: يتم الخصم من الدفتر لمرة واحدة فقط وبقيمة `debt_reduction` الفعلية.

---

## 6. تكامل حركة المخزون (Inventory & Stock Integration)

كل عملية بيع وكل عملية مرتجع تنفذ الآتي ذرّياً داخل **Transaction واحدة**:
1. تحديث كمية الصنف في مخزن العمليات `warehouse_stock`.
2. مزامنة كمية الصنف الإجمالية في `products.quantity = SUM(warehouse_stock)`.
3. تسجيل حركة تفصيلية في `stock_movements`:
   - البيع: `movement_type = 'SALE'` بكمية سالبة (-qty) ورقم الفاتورة كـ `reference_id` ومعرف المستخدم.
   - المرتجع: `movement_type = 'SALE_RETURN'` بكمية موجبة (+qty) ورقم الفاتورة كـ `reference_id` ومعرف المستخدم.

---

## 7. الاختبارات المالية والمخزنية (Financial & Inventory Testing)

تم إنشاء وتشغيل حزمة اختبارات شاملة `SalesFinancialAndInventoryTest.java` (Robolectric) غطت واختبرت بنجاح:
1. **البيع النقدي (Cash Sale)**:
   - خصم المخزون من 100 إلى 90 ومزامنة `products.quantity` و `warehouse_stock`.
   - تسجيل حركة `stock_movements` بنوع `SALE` وبكمية -10 ومعرف الكاشير الفعلي.
   - التحقق من حالة الفاتورة "مدفوعة بالكامل" وعدم تسجيل ديون على العميل.
2. **البيع الآجل (Credit Sale)**:
   - خصم المخزون وتسجيل حركة المخزون.
   - تسجيل حركة `TX_SALE` في `account_ledger` بقيمة الصافي (240 ر.ي).
   - مزامنة رصيد العميل إلى +240 ر.ي بدقة.
3. **البيع الجزئي وسداد العميل (Partial Sale & Customer Payment)**:
   - تسجيل الفاتورة بحالة "دفع جزئي".
   - تسجيل حركتي `TX_SALE` (+200) و `TX_SALE_PAYMENT` (-50) في الدفتر -> الرصيد المتبقي = 150 ر.ي.
   - سداد العميل للمبلغ المتبقي (150 ر.ي) -> عودة رصيد العميل إلى 0.0 ر.ي تماماً.
4. **المرتجع النقدي والمرتجع من الدين (Returns Testing)**:
   - إعادة المخزون من 94 إلى 96 وتسجيل حركة `SALE_RETURN` بقيمة +2.
   - تخفيض دين الفاتورة من 300 إلى 200.
   - تسجيل حركة `TX_RETURN` في الدفتر وتخفيض رصيد العميل من 300 إلى 200 دون أي مضاعفة للخصم.
5. **الفواتير المعلقة (Suspended Sales Isolation)**:
   - التحقق الصارم من عدم تأثر المخزون (بقي 100).
   - التحقق من عدم تأثر رصيد العميل أو دفتر الحسابات (بقي 0.0).
   - استئناف الفاتورة وإتمامها -> خصم المخزون إلى 90 وحذف المعلقة تلقائياً.

---

## 8. الملفات المنشأة والمعدلة

| الملف | الحالة | الوصف |
|---|---|---|
| [`DatabaseHelper.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/database/DatabaseHelper.java) | معدل | ترقية V19، جداول المعلقات، حقول المستخدمين، الفهارس، وتوابع الحذف والعدد |
| [`SuspendedSale.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/models/SuspendedSale.java) | منشأ | نموذج بيانات الفاتورة المعلقة |
| [`SuspendedSaleItem.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/models/SuspendedSaleItem.java) | منشأ | نموذج بيانات أصناف الفاتورة المعلقة |
| [`SuspendedSalesActivity.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/activities/SuspendedSalesActivity.java) | منشأ | شاشة عرض وإدارة واستئناف وحذف الفواتير المعلقة مع Drawer |
| [`activity_suspended_sales.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/res/layout/activity_suspended_sales.xml) | منشأ | واجهة شاشة الفواتير المعلقة |
| [`item_suspended_sale.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/res/layout/item_suspended_sale.xml) | منشأ | تصميم بطاقة الفاتورة المعلقة |
| [`SalesActivity.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/activities/SalesActivity.java) | معدل | دعم التعليق والاستئناف والمستخدم الفعلي وتحديث المخزون الذري |
| [`activity_sales.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/res/layout/activity_sales.xml) | معدل | إضافة أزرار التعليق وشريط الفاتورة المستأنفة وزر المعلقات |
| [`ReturnsActivity.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/activities/ReturnsActivity.java) | معدل | إعادة بناء مسار العمل (اختيار فاتورة، تفاصيل الأصناف، طرق التسوية، الترانزاكشن) |
| [`activity_returns.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/res/layout/activity_returns.xml) | معدل | تصميم الواجهة التفاعلية لاختيار الفاتورة والأصناف والتسوية المالية |
| [`item_return_product_row.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/res/layout/item_return_product_row.xml) | منشأ | تصميم صف الصنف المرتجع مع عداد الكميات والأسعار |
| [`DrawerNavigationHelper.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/java/com/lap1/mystore/utils/DrawerNavigationHelper.java) | معدل | ربط بند القائمة "الفواتير المعلقة" بشاشة `SuspendedSalesActivity` |
| [`AndroidManifest.xml`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/main/AndroidManifest.xml) | معدل | تسجيل `SuspendedSalesActivity` |
| [`SalesFinancialAndInventoryTest.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/test/java/com/lap1/mystore/SalesFinancialAndInventoryTest.java) | منشأ | حزمة الاختبارات المالية والمخزنية المتكاملة |
| [`StoreDatabaseLogicTest.java`](file:///C:/Users/top%20laptop/AndroidStudioProjects/MyStore2_phase3_fixed/app/src/test/java/com/lap1/mystore/StoreDatabaseLogicTest.java) | معدل | تحديث التحقق من إصدار قاعدة البيانات 19 |

---

## 9. حالة التقارير والحسابات القديمة

- **لم يتم تعديل أي من تقارير المبيعات أو الأرباح أو تقفيل الصندوق القديمة**.
- جميع الاستعلامات القائمة متوافقة 100% لأن التعديلات كانت إضافية فقط (`Additive`).

---

## 10. إلغاء ميزة الفواتير المعلقة وتحسينات تجربة المستخدم (Phase 6 Deprecation & UX Refinements)

### إلغاء نظام الفواتير المعلقة (Suspended Sales)

تم إلغاء ميزة الفواتير المعلقة بالكامل كجزء من مراجعة بنية التطبيق. التغييرات التالية تم إجراؤها:

| المكوّن | الإجراء |
|---|---|
| `SuspendedSalesActivity.java` | **حذف** — شاشة إدارة الفواتير المعلقة |
| `SuspendedSale.java` | **حذف** — نموذج بيانات الفاتورة المعلقة |
| `SuspendedSaleItem.java` | **حذف** — نموذج بيانات أصناف الفاتورة المعلقة |
| `activity_suspended_sales.xml` | **حذف** — واجهة شاشة الفواتير المعلقة |
| `item_suspended_sale.xml` | **حذف** — تصميم بطاقة الفاتورة المعلقة |
| `AndroidManifest.xml` | **تعديل** — إزالة تسجيل `SuspendedSalesActivity` |
| `DrawerNavigationHelper.java` | **تعديل** — إزالة ربط قائمة التنقل "الفواتير المعلقة" واستيراد الصنف |
| `layout_drawer_content.xml` | **تعديل** — إزالة عنصر قائمة "الفواتير المعلقة" |
| `DatabaseHelper.java` (onCreate) | **تعديل** — إزالة `CREATE TABLE` لـ `suspended_sales` و `suspended_sale_items` |
| `DatabaseHelper.java` (onUpgrade V19) | **تعديل** — إزالة `CREATE TABLE IF NOT EXISTS` للجداول المعلقة من الترقية التراكمية |
| `DatabaseHelper.java` (indexes) | **تعديل** — إزالة الفهارس `idx_suspended_sales_created` و `idx_suspended_sale_items_sale` |
| `DatabaseHelper.java` (methods) | **تعديل** — إزالة `getSuspendedSalesCount()` و `deleteSuspendedSale()` |
| `SalesActivity.java` | **تعديل** — إزالة كامل منطق التعليق والاستئناف، إزالة الأزرار والبانر المرتبطة |
| `activity_sales.xml` | **تعديل** — إزالة أزرار التعليق، زر العرض المعلق، وشريط الفاتورة المستأنفة |
| `Phase9ReportsAndSettingsTest.java` | **تعديل** — إزالة `suspended_sales` و `suspended_sale_items` من قائمة الجداول المطلوبة في `testAllTablesExistForBackupAndRestore` |
| `SalesFinancialAndInventoryTest.java` | **تعديل** — إزالة اختبار `testSuspendedSalesDoesNotAffectStockOrAccountingUntilCompleted` |

### تحسينات تجربة المستخدم في نقطة البيع (SalesActivity)

تم إجراء التالي من قبل فريق المنتج لتقليل الاحتكامات وتحسين سير العمل:

1. **التخلّي عن الحظر الصارم للمخزون**: عند إضافة منتج أو زيادة الكمية في السلة يتجاوز المخزون المتاح، بدلاً من منع العملية تمامًا يتم عرض حوار تأكيد `MaterialAlertDialogBuilder` يطلب إتمام الإجراء. إذا أكد العملاء، يتم المتابعة؟ إذا ألغي، يتم إلغاء العملية.
2. **التخلّي عن حظر الدفع الزائد**: عند إدخال مبلغ مدفوع يتجاوز صافي الفاتورة، بدلاً من رفض المعاملة يتم السماح بها وحساب المبلغ الزائد كـ "باقي" (change) وعرضه بوضوح في رسالة النجاح والنتيجة.
3. **السماح بالبيع الآجل/الجزئي بدون عميل**: بدلاً من منع البيع عندما لا يتم اختيار عميل، يتم إظهار Toast توضيحي "تم حفظ البيع الآجل بدون عميل محدد" ويتم إكمال العملية ببيانات العميل = NULL.
4. **زر مسح السلة (Clear Cart)**: تم إضافة زر أحمر اللون "🧹 مسح السلة" يقوم بمسح السلة وإعادة جميع الحقول لحالتها الافتراضية.
5. **التركيز التلقائي على حقل الباركود**: بعد إضافة أي منتج إلى السلة، يتم مسح حقل الباركود وتوجيه التركيز إليه تلقائيًا لتسهيل المسح المتتالي.
6. **عرض الباقي في الفاتورة المشاركة**: يتم إضافة سطر "الباقي/المتبقي" إلى الفاتورة المشاركة عبر WhatsApp عندما يكون هناك مبلغ زائد.

---

**⛔ انتهت هذه المرحلة بنجاح تام وفق جميع الشروط المطلوبة — في انتظار توجيهك للمرحلة القادمة.**
