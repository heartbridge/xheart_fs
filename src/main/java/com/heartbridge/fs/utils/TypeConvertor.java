package com.heartbridge.fs.utils;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class TypeConvertor {


    public static String toString(Object o){
        if(o == null) return null;

        if(o instanceof String){
            return (String) o;
        }else{
            return o.toString();
        }
    }

    public static Integer toInteger(Object o){
        if(o == null) return null;

        if(o instanceof Integer){
            return (Integer) o;
        }else{
            return Integer.valueOf(o.toString());
        }
    }

    public static Long toLong(Object o){
        if(o == null) return null;

        if(o instanceof Long){
            return (Long) o;
        }else{
            return Long.valueOf(o.toString());
        }
    }

    public static Double toDouble(Object o){
        if(o == null) return null;

        if(o instanceof Double){
            return (Double) o;
        }else{
            return Double.valueOf(o.toString());
        }
    }

    public static Float toFloat(Object o){
        if(o == null) return null;

        if(o instanceof Float){
            return (Float) o;
        }else{
            return Float.valueOf(o.toString());
        }
    }

    public static Boolean toBoolean(Object o){
        if(o == null) return null;

        if(o instanceof Boolean){
            return (Boolean) o;
        }else{
            return Boolean.valueOf(o.toString());
        }
    }

    public static Integer[] toIntegerArray(Object[] array){
        if(array == null) return null;

        if(array instanceof Integer[]) return (Integer[]) array;

        Integer[] result = new Integer[array.length];
        for(int p = 0, l = array.length; p < l; p++){
            result[p] = toInteger(array[p]);
        }
        return result;
    }

    public static Long[] toLongArray(Object[] array){
        if(array == null) return null;

        if(array instanceof Long[]) return (Long[]) array;

        Long[] result = new Long[array.length];
        for(int p = 0, l = array.length; p < l; p++){
            result[p] = toLong(array[p]);
        }
        return result;
    }

    public static Double[] toDoubleArray(Object[] array){
        if(array == null) return null;

        if(array instanceof Double[]) return (Double[]) array;

        Double[] result = new Double[array.length];
        for(int p = 0, l = array.length; p < l; p++){
            result[p] = toDouble(array[p]);
        }
        return result;
    }

    public static Float[] toFloatArray(Object[] array){
        if(array == null) return null;

        if(array instanceof Float[]) return (Float[]) array;

        Float[] result = new Float[array.length];
        for(int p = 0, l = array.length; p < l; p++){
            result[p] = toFloat(array[p]);
        }
        return result;
    }

    public static Boolean[] toBooleanArray(Object[] array){
        if(array == null) return null;

        if(array instanceof Boolean[]) return (Boolean[]) array;

        Boolean[] result = new Boolean[array.length];
        for(int p = 0, l = array.length; p < l; p++){
            result[p] = toBoolean(array[p]);
        }
        return result;
    }
}
