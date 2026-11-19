import { Image, Modal, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import { X } from 'lucide-react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface Props {
  image: ImagePickerAsset | null;
  onClose: () => void;
}

export default function GroupChatImagePreview({ image, onClose }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const availableWidth = Math.max(1, width - 32);
  const availableHeight = Math.max(1, height - insets.top - insets.bottom - 120);
  const imageWidth = image?.width && image.width > 0 ? image.width : availableWidth;
  const imageHeight = image?.height && image.height > 0 ? image.height : availableHeight;
  const scale = Math.min(availableWidth / imageWidth, availableHeight / imageHeight);

  return <Modal visible={Boolean(image)} animationType="fade" transparent
    statusBarTranslucent onRequestClose={onClose}>
    <View style={[styles.overlay, { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }]}>
      <View style={styles.header}>
        <Text style={styles.title}>Image preview</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="Close image preview"
          onPress={onClose} style={styles.close}>
          <X size={22} color="#253043" />
        </Pressable>
      </View>
      <View style={styles.imageArea}>
        {image && <Image source={{ uri: image.uri }} resizeMode="contain"
          accessibilityLabel="Selected image preview"
          style={{ width: imageWidth * scale, height: imageHeight * scale }} />}
      </View>
      <Text style={styles.hint}>Close to return to your message</Text>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: '#101A2B', paddingHorizontal: 16 },
  header: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: '#fff', fontSize: 17, fontWeight: '800' },
  close: { width: 42, height: 42, borderRadius: 15, backgroundColor: '#fff',
    alignItems: 'center', justifyContent: 'center' },
  imageArea: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  hint: { color: '#E4EAF3', fontSize: 12, textAlign: 'center', paddingVertical: 8 },
});
