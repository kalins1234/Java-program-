public class MergeSortedArrays {

    public static int[] mergeSorted(int[] arr1, int[] arr2) {
        int n1 = arr1.length;
        int n2 = arr2.length;
        int[] ans = new int[n1 + n2];

        int i = 0; // pointer for arr1
        int j = 0; // pointer for arr2
        int k = 0; // pointer for ans

        // Compare elements from both arrays and pick the smaller one
        while (i < n1 && j < n2) {
            if (arr1[i] <= arr2[j]) {
                ans[k++] = arr1[i++];
            } else {
                ans[k++] = arr2[j++];
            }
        }

        // Copy remaining elements of arr1 (if any)
        while (i < n1) {
            ans[k++] = arr1[i++];
        }

        // Copy remaining elements of arr2 (if any)
        while (j < n2) {
            ans[k++] = arr2[j++];
        }

        return ans;
    }

    public static void main(String[] args) {
        int[] arr1 = {1, 3, 5, 7};
        int[] arr2 = {2, 5, 7, 8};

        int[] ans = mergeSorted(arr1, arr2);

        System.out.print("Merged Sorted Array: ");
        for (int i = 0; i < ans.length; i++) {
            System.out.print(ans[i]);
            if (i < ans.length - 1) System.out.print(", ");
        }
        System.out.println();
    }
}
