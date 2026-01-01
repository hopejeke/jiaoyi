/**
 * 大整数 ID 处理工具函数
 * 用于解决 JavaScript 大整数精度丢失问题
 * 
 * 问题：JavaScript 的 Number 类型只能安全表示 ±2^53 的整数
 * 雪花算法生成的 ID 超过这个范围，会导致精度丢失
 * 
 * 解决方案：所有 ID 都作为字符串处理
 */

/**
 * 将值转换为字符串 ID（如果可能）
 * @param {any} value - 要转换的值
 * @returns {string} 字符串类型的 ID
 */
function toIdString(value) {
    if (value == null || value === '') {
        return null;
    }
    return String(value);
}

/**
 * 从对象中提取 ID 并转换为字符串
 * @param {object} obj - 对象
 * @param {string} idField - ID 字段名，默认为 'id'
 * @returns {string|null} 字符串类型的 ID
 */
function getIdAsString(obj, idField = 'id') {
    if (!obj || obj[idField] == null) {
        return null;
    }
    return String(obj[idField]);
}

/**
 * 从 URL 参数中获取 ID（作为字符串）
 * @param {string} paramName - 参数名
 * @returns {string|null} 字符串类型的 ID
 */
function getUrlParamAsId(paramName) {
    const urlParams = new URLSearchParams(window.location.search);
    const value = urlParams.get(paramName);
    return value ? String(value) : null;
}

/**
 * 确保对象中的所有 ID 字段都是字符串类型
 * @param {object} obj - 要处理的对象
 * @param {string[]} idFields - ID 字段名数组，默认为 ['id', 'orderId', 'productId', 'merchantId', 'storeId', 'userId']
 * @returns {object} 处理后的对象
 */
function ensureIdsAsStrings(obj, idFields = ['id', 'orderId', 'productId', 'merchantId', 'storeId', 'userId', 'couponId', 'skuId']) {
    if (!obj || typeof obj !== 'object') {
        return obj;
    }
    
    const result = { ...obj };
    idFields.forEach(field => {
        if (result[field] != null) {
            result[field] = String(result[field]);
        }
    });
    
    return result;
}

/**
 * 确保数组中的所有对象的 ID 字段都是字符串类型
 * @param {array} arr - 要处理的数组
 * @param {string[]} idFields - ID 字段名数组
 * @returns {array} 处理后的数组
 */
function ensureArrayIdsAsStrings(arr, idFields = ['id', 'orderId', 'productId', 'merchantId', 'storeId', 'userId', 'couponId', 'skuId']) {
    if (!Array.isArray(arr)) {
        return arr;
    }
    return arr.map(item => ensureIdsAsStrings(item, idFields));
}

// 导出到全局作用域（如果使用模块系统，可以改为 export）
if (typeof window !== 'undefined') {
    window.LongIdUtils = {
        toIdString,
        getIdAsString,
        getUrlParamAsId,
        ensureIdsAsStrings,
        ensureArrayIdsAsStrings
    };
}












