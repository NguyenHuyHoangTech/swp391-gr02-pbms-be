
const fs = require("fs");
const path = "src/main/java/com/pbms/modules/finance/controller/DashboardController.java";
let content = fs.readFileSync(path, "utf-8");

content = content.replace(
    /(\s*)@GetMapping\("\/revenue"\)/,
    `$1/**\n     * @Function: getRevenueOverview\n     * @Description: L?y d? li?u t?ng quan v? doanh thu trong m?t kho?ng th?i gian nh?t d?nh.\n     * @Logic_Steps:\n     * 1. Nh?n tham s? startDate và endDate t? query URL.\n     * 2. G?i dashboardService d? x? lý logic l?y d? li?u t?ng quan doanh thu.\n     * 3. Tr? v? k?t qu? du?i d?ng ApiResponse kèm theo thông báo thành công.\n     * \n     * @param startDate Ngày b?t d?u (d?nh d?ng YYYY-MM-DD)\n     * @param endDate Ngày k?t thúc (d?nh d?ng YYYY-MM-DD)\n     * @return ResponseEntity ch?a ApiResponse v?i d? li?u t?ng quan doanh thu\n     */$1@GetMapping("/revenue")`
);
content = content.replace(/"The amount of revenue is the same"/, `"L?y d? li?u t?ng quan doanh thu thành công"`);

content = content.replace(
    /(\s*)@GetMapping\("\/operational"\)/,
    `$1/**\n     * @Function: getOperationalOverview\n     * @Description: L?y d? li?u t?ng quan v? ho?t d?ng v?n hành c?a bãi d? xe trong m?t ngày c? th?.\n     * @Logic_Steps:\n     * 1. Nh?n tham s? date (không b?t bu?c) t? query URL.\n     * 2. G?i dashboardService d? truy xu?t d? li?u v?n hành.\n     * 3. Tr? v? k?t qu? du?i d?ng ApiResponse.\n     * \n     * @param date Ngày c?n l?y d? li?u (d?nh d?ng ISO YYYY-MM-DD), n?u không truy?n s? l?y ngày hi?n t?i.\n     * @return ResponseEntity ch?a ApiResponse v?i d? li?u ho?t d?ng v?n hành\n     */$1@GetMapping("/operational")`
);
content = content.replace(/"Operational overview retrieved successfully"/, `"L?y d? li?u t?ng quan ho?t d?ng thành công"`);

content = content.replace(
    /\/\*\*[^*]*getHourlyOccupancy[^*]*\*\//,
    `/**\n     * @Function: getHourlyOccupancy\n     * @Description: L?y d? li?u lu?ng xe dang có m?t trong bãi theo t?ng gi? trong ngày (d? v? bi?u d? dâng nu?c).\n     * @Logic_Steps:\n     * 1. Nh?n tham s? date (b?t bu?c) t? query URL.\n     * 2. G?i dashboardService tính toán m?t d? xe theo t?ng gi?.\n     * 3. Tr? v? danh sách d? li?u gi? kèm theo k?t qu?.\n     * \n     * @param date Ngày c?n phân tích (d?nh d?ng ISO YYYY-MM-DD)\n     * @return ResponseEntity ch?a ApiResponse v?i danh sách lu?ng xe theo gi?\n     */`
);
content = content.replace(/"Success"/, `"L?y d? li?u m?t d? xe theo gi? thành công"`);

content = content.replace(
    /\/\*\*[^*]*getHourlyFlow[^*]*\*\//,
    `/**\n     * @Function: getHourlyFlow\n     * @Description: L?y d? li?u luu lu?ng xe vào/ra bãi d? trong gi? cao di?m ho?c phân b? trong ngày.\n     * @Logic_Steps:\n     * 1. Nh?n tham s? date (b?t bu?c) t? query URL.\n     * 2. G?i dashboardService phân tích luu lu?ng xe ra vào.\n     * 3. Tr? v? k?t qu? du?i d?ng ApiResponse.\n     * \n     * @param date Ngày c?n phân tích (d?nh d?ng ISO YYYY-MM-DD)\n     * @return ResponseEntity ch?a ApiResponse v?i danh sách luu lu?ng xe\n     */`
);
content = content.replace(/"The price of this product is as high as the price."/, `"L?y d? li?u luu lu?ng xe v?o/ra thành công"`);

content = content.replace(
    /\/\*\*[^*]*getMacroTrends[^*]*\*\//,
    `/**\n     * @Function: getMacroTrends\n     * @Description: L?y t? h?p d? li?u phân tích vi mô (Macro Trends) cho báo cáo th?ng kê chuyên sâu.\n     * @Logic_Steps:\n     * 1. Nh?n các tham s? startDate, endDate và category (có th? r?ng) t? query URL.\n     * 2. G?i dashboardService th?c thi query phân tích vi mô d?a trên tham s? l?c.\n     * 3. Tr? v? d?i tu?ng Map d? li?u phân tích chi ti?t.\n     * \n     * @param startDate Ngày b?t d?u\n     * @param endDate Ngày k?t thúc\n     * @param category Phân lo?i c?n l?c (n?u có)\n     * @return ResponseEntity ch?a ApiResponse v?i d? li?u vi mô\n     */`
);
content = content.replace(/"This is a great solution to the problem."/, `"L?y d? li?u xu hu?ng vi mô thành công"`);

fs.writeFileSync(path, content, "utf-8");
console.log("Done");

