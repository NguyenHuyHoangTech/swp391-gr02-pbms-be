
const fs = require("fs");
const path = "src/main/java/com/pbms/modules/finance/controller/PaymentController.java";
let content = fs.readFileSync("D:/SWP391_Test/ParkingManagement/pbms-be/src/main/java/com/pbms/modules/finance/controller/PaymentController.java", "utf-8");

content = content.replace(
    /(\s*)\/\*\*\s*\n\s*\*\s*POST \/api\/v1\/payments\/initialize\s*\n\s*\*\s*Validates business payload and generates payment link\s*\n\s*\*\/\s*\n\s*@PostMapping\("\/initialize"\)/,
    `$1/**\n     * @Function: initializePayment\n     * @Description: Xác th?c d? li?u nghi?p v? và t?o liên k?t thanh toán.\n     * @Logic_Steps:\n     * 1. L?y thông tin email c?a ngu?i dùng hi?n t?i t? SecurityContext.\n     * 2. Xác d?nh s? ti?n (amount) và c?ng thanh toán (gateway).\n     * 3. D?a trên gateway (PAYOS, PAYPAL, VNPAY) d? g?i API t?o URL thanh toán tuong ?ng.\n     * 4. G?i paymentValidatorService d? kh?i t?o don hàng thanh toán nháp trong DB.\n     * 5. Tr? v? thông tin thanh toán (URL, QR code, tr?ng thái) trong ResponseEntity.\n     * \n     * @param request Yêu c?u ch?a thông tin giao d?ch c?n thanh toán\n     * @return ResponseEntity ch?a ApiResponse v?i URL thanh toán\n     */\n    @PostMapping("/initialize")`
);

content = content.replace(
    /(\s*)\/\*\*\s*\n\s*\*\s*POST \/api\/v1\/payments\/paypal\/capture\s*\n\s*\*\s*Capture PayPal order\s*\n\s*\*\/\s*\n\s*@PostMapping\("\/paypal\/capture"\)/,
    `$1/**\n     * @Function: capturePayPalOrder\n     * @Description: Xác nh?n hoàn t?t giao d?ch thông qua PayPal.\n     * @Logic_Steps:\n     * 1. Nh?n mã token t? request body. Ném l?i n?u token r?ng.\n     * 2. G?i payPalStrategy d? capture giao d?ch thông qua API c?a PayPal.\n     * 3. N?U thành công, c?p nh?t tr?ng thái c?a don hàng tuong ?ng thành "PAID" trong DB.\n     * 4. Tr? v? k?t qu? thành công ho?c th?t b?i.\n     * \n     * @param requestBody Map ch?a mã token giao d?ch PayPal\n     * @return ResponseEntity ch?a ApiResponse v?i tr?ng thái c?p nh?t\n     */\n    @PostMapping("/paypal/capture")`
);

content = content.replace(
    /(\s*)\/\*\*\s*\n\s*\*\s*POST \/api\/v1\/payments\/payos\/capture\s*\n\s*\*\s*Capture PayOS order\s*\n\s*\*\/\s*\n\s*@PostMapping\("\/payos\/capture"\)/,
    `$1/**\n     * @Function: capturePayOsOrder\n     * @Description: Xác nh?n hoàn t?t giao d?ch thông qua PayOS.\n     * @Logic_Steps:\n     * 1. Nh?n mã token (mã giao d?ch PayOS) t? request body.\n     * 2. G?i payOsStrategy d? ki?m tra tr?ng thái thanh toán.\n     * 3. N?U thanh toán thành công, c?p nh?t tr?ng thái don hàng thành "PAID" trong DB.\n     * 4. Tr? v? k?t qu? xác nh?n thanh toán.\n     * \n     * @param requestBody Map ch?a mã token giao d?ch PayOS\n     * @return ResponseEntity ch?a ApiResponse v?i tr?ng thái c?p nh?t\n     */\n    @PostMapping("/payos/capture")`
);

content = content.replace(
    /(\s*)\/\*\*\s*\n\s*\*\s*POST \/api\/v1\/payments\/execute-action\s*\n\s*\*\s*Finalize transaction and run business logic\s*\n\s*\*\/\s*\n\s*@PostMapping\("\/execute-action"\)/,
    `$1/**\n     * @Function: executeAction\n     * @Description: K?t thúc quá trình giao d?ch và th?c thi nghi?p v? h? th?ng.\n     * @Logic_Steps:\n     * 1. Nh?n mã token t? request body.\n     * 2. G?i paymentValidatorService.executeAction() d? ch?y logic nghi?p v? d?a trên payload c?a don hàng.\n     * 3. Tr? v? k?t qu? thành công n?u nghi?p v? th?c thi t?t.\n     * 4. B?T (CATCH) m?i ngo?i l? phát sinh trong quá trình ch?y nghi?p v?.\n     * 5. N?U có l?i h? th?ng, t? d?ng kích ho?t ti?n trình hoàn ti?n (processRefundForFailedAction) cho ngu?i dùng.\n     * 6. Tr? v? thông báo l?i kèm theo tr?ng thái báo cáo hoàn ti?n.\n     * \n     * @param requestBody Map ch?a mã token don hàng\n     * @return ResponseEntity ch?a k?t qu? th?c thi nghi?p v?\n     */\n    @PostMapping("/execute-action")`
);

// Add the missing package and import statement that format_finance.js did
content = "package com.pbms.modules.finance.controller;\n\n/**\n * @Author: Võ Trung Hi?u\n * @Date: 2026-07-05\n * @Description: Controller managing PaymentController endpoints.\n * @Dependencies: \n */\n\n\nimport com.pbms.common.dto.ApiResponse;\n" + content.substring(content.indexOf("import lombok.RequiredArgsConstructor;"));


fs.writeFileSync(path, content, "utf-8");
console.log("Done updating PaymentController.java");

