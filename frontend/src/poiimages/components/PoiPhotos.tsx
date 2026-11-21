import { useEffect, useState } from 'react';
import {
  ActivityIndicator, Image, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View,
} from 'react-native';
import { useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { Camera, ImagePlus, Trash2, X } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import {
  CAPTION_MAX_LENGTH, deletePoiImage, listPoiImages, uploadPoiImage, type PoiImage,
} from '../api/poiImages';
import { isSafeImageUrl } from '../limits';

const BLUE = '#0065EB';
const INK = '#363d47';
const MUTED = '#626b78';

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

/** A thumbnail served by ImageKit from a signed URL. */
function Thumbnail({ photo, onOpen }: { photo: PoiImage; onOpen(): void }) {
  const [failed, setFailed] = useState(false);
  const safe = isSafeImageUrl(photo.thumbnailUrl);
  return (
    <Pressable accessibilityRole="imagebutton"
               accessibilityLabel={photo.caption ? `Open photo: ${photo.caption}` : `Open photo added by ${photo.uploadedByName}`}
               onPress={onOpen} style={styles.thumb}>
      {safe && !failed
        ? <Image source={{ uri: photo.thumbnailUrl }} style={styles.thumbImage} onError={() => setFailed(true)} accessibilityIgnoresInvertColors />
        : <Camera size={20} color="#9aa3ae" />}
    </Pressable>
  );
}

/** Full-size photo with caption, author and, for the uploader, a delete button. */
function PhotoViewer({ photo, onClose, onDeleted }: { photo: PoiImage; onClose(): void; onDeleted(): void }) {
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(isSafeImageUrl(photo.imageUrl) ? null : 'This photo cannot be shown.');
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function remove() {
    setDeleting(true);
    setError(null);
    try {
      await deletePoiImage(photo.id);
      onDeleted();
    } catch (failure) {
      setError(message(failure));
      setDeleting(false);
      setConfirming(false);
    }
  }

  const added = new Date(photo.createdAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.viewerBackdrop}>
        <View style={styles.viewer} accessibilityViewIsModal>
          <Pressable accessibilityRole="button" accessibilityLabel="Close photo" onPress={onClose} style={styles.viewerClose}>
            <X size={22} color="#ffffff" />
          </Pressable>
          <View style={[styles.viewerImageBox, { aspectRatio: photo.width / photo.height }]}>
            {!error && (
              <Image source={{ uri: photo.imageUrl }} style={styles.viewerImage} resizeMode="contain"
                     onLoad={() => setLoaded(true)} onError={() => setError('Could not load the photo. Close it and try again.')}
                     accessibilityIgnoresInvertColors />
            )}
            {!loaded && !error && <ActivityIndicator color="#ffffff" accessibilityLabel="Loading photo" style={styles.viewerSpinner} />}
          </View>
          {!!photo.caption && <Text style={styles.viewerCaption}>{photo.caption}</Text>}
          <Text style={styles.viewerMeta}>Added by {photo.uploadedByName} · {added}</Text>
          {error && <Text accessibilityRole="alert" style={styles.viewerError}>{error}</Text>}
          {photo.mine && (confirming ? (
            <View style={styles.row}>
              <Text style={styles.viewerMeta}>Delete this photo?</Text>
              <Pressable accessibilityRole="button" disabled={deleting} onPress={remove} style={[styles.dangerButton, deleting && styles.dim]}>
                <Text style={styles.dangerText}>{deleting ? 'Deleting…' : 'Delete'}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" disabled={deleting} onPress={() => setConfirming(false)} style={styles.plainButton}>
                <Text style={styles.plainText}>Cancel</Text>
              </Pressable>
            </View>
          ) : (
            <Pressable accessibilityRole="button" onPress={() => setConfirming(true)} style={styles.row}>
              <Trash2 size={16} color="#ff8a80" /><Text style={styles.deleteLink}>Delete photo</Text>
            </Pressable>
          ))}
        </View>
      </View>
    </Modal>
  );
}

/**
 * Photos section of the POI details card (SCRUM-42). Signed-in users see the photos and can add their own;
 * signed-out users are invited to sign in. Remount it per POI (key it by the POI id) to reset its state.
 */
export default function PoiPhotos({ poiId, poiName }: { poiId: string; poiName: string }) {
  const router = useRouter();
  const { session } = useSession();
  const signedIn = Boolean(session);
  const [photos, setPhotos] = useState<PoiImage[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [open, setOpen] = useState<PoiImage | null>(null);
  const [pending, setPending] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [caption, setCaption] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  useEffect(() => {
    if (!signedIn) return;
    let active = true;
    listPoiImages(poiId)
      .then(list => { if (active) { setPhotos(list); setError(null); } })
      .catch(failure => { if (active) setError(message(failure)); });
    return () => { active = false; };
  }, [poiId, signedIn, attempt]);

  async function choosePhoto() {
    setUploadError(null);
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!result.canceled && result.assets[0]) {
      setPending(result.assets[0]);
      setCaption('');
    }
  }

  async function upload() {
    if (!pending) return;
    setUploading(true);
    setUploadError(null);
    try {
      const saved = await uploadPoiImage(poiId, pending, caption);
      setPhotos(current => [saved, ...(current ?? [])]);
      setPending(null);
    } catch (failure) {
      setUploadError(message(failure));
    } finally {
      setUploading(false);
    }
  }

  if (!signedIn) {
    return (
      <View style={styles.section}>
        <Text style={styles.title}>Photos</Text>
        <Pressable accessibilityRole="link" onPress={() => router.push('/login')}>
          <Text style={styles.link}>Sign in to see and add photos of {poiName}</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Text style={styles.title}>Photos{photos?.length ? ` (${photos.length})` : ''}</Text>
        {!pending && (
          <Pressable accessibilityRole="button" accessibilityLabel={`Add a photo of ${poiName}`} onPress={choosePhoto} style={styles.addButton}>
            <ImagePlus size={16} color={BLUE} /><Text style={styles.addText}>Add photo</Text>
          </Pressable>
        )}
      </View>

      {pending && (
        <View style={styles.pending}>
          <Image source={{ uri: pending.uri }} style={styles.pendingPreview} accessibilityLabel="Selected photo" />
          <View style={styles.pendingBody}>
            <TextInput value={caption} onChangeText={setCaption} maxLength={CAPTION_MAX_LENGTH} editable={!uploading}
                       placeholder="Caption (optional)" accessibilityLabel="Photo caption" style={styles.captionInput} />
            <View style={styles.row}>
              <Pressable accessibilityRole="button" disabled={uploading} onPress={upload} style={[styles.primaryButton, uploading && styles.dim]}>
                <Text style={styles.primaryText}>{uploading ? 'Uploading…' : 'Upload'}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" disabled={uploading} onPress={() => { setPending(null); setUploadError(null); }} style={styles.plainButton}>
                <Text style={[styles.plainText, styles.mutedText]}>Cancel</Text>
              </Pressable>
            </View>
          </View>
        </View>
      )}
      {uploadError && <Text accessibilityRole="alert" style={styles.error}>{uploadError}</Text>}

      {error ? (
        <View style={styles.row}>
          <Text accessibilityRole="alert" style={styles.error}>{error}</Text>
          <Pressable accessibilityRole="button" onPress={() => { setError(null); setPhotos(null); setAttempt(value => value + 1); }}>
            <Text style={styles.link}>Try again</Text>
          </Pressable>
        </View>
      ) : photos === null ? (
        <ActivityIndicator color={BLUE} accessibilityLabel="Loading photos" style={styles.loading} />
      ) : photos.length === 0 ? (
        !pending && <Text style={styles.empty}>No photos yet. Be the first to add one.</Text>
      ) : (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.strip}>
          {photos.map(photo => <Thumbnail key={photo.id} photo={photo} onOpen={() => setOpen(photo)} />)}
        </ScrollView>
      )}

      {open && (
        <PhotoViewer photo={open} onClose={() => setOpen(null)}
                     onDeleted={() => { setPhotos(current => (current ?? []).filter(item => item.id !== open.id)); setOpen(null); }} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderColor: '#eef1f5', gap: 8 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontSize: 14, fontWeight: '700', color: INK },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  addButton: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingVertical: 4, paddingHorizontal: 8, borderRadius: 999, borderWidth: 1, borderColor: '#cfe0fb' },
  addText: { color: BLUE, fontWeight: '600', fontSize: 13 },
  strip: { gap: 8 },
  thumb: { width: 72, height: 72, borderRadius: 10, overflow: 'hidden', backgroundColor: '#eef4ff', alignItems: 'center', justifyContent: 'center' },
  thumbImage: { width: '100%', height: '100%' },
  empty: { color: MUTED, fontSize: 13 },
  loading: { alignSelf: 'flex-start' },
  error: { color: '#b42318', fontSize: 13 },
  link: { color: BLUE, fontWeight: '600', fontSize: 13 },
  pending: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  pendingPreview: { width: 72, height: 72, borderRadius: 10, backgroundColor: '#eef4ff' },
  pendingBody: { flex: 1, gap: 8 },
  captionInput: { borderWidth: 1, borderColor: '#d5dce6', borderRadius: 10, paddingHorizontal: 10, paddingVertical: 8, fontSize: 14, color: INK },
  primaryButton: { backgroundColor: BLUE, borderRadius: 10, paddingVertical: 8, paddingHorizontal: 14 },
  primaryText: { color: '#ffffff', fontWeight: '700' },
  plainButton: { paddingVertical: 8, paddingHorizontal: 10 },
  plainText: { color: '#ffffff', fontWeight: '600' },
  mutedText: { color: MUTED },
  dim: { opacity: 0.6 },
  viewerBackdrop: { flex: 1, backgroundColor: 'rgba(10, 14, 20, 0.92)', alignItems: 'center', justifyContent: 'center', padding: 16 },
  viewer: { width: '100%', maxWidth: 900, alignItems: 'center', gap: 10 },
  viewerClose: { alignSelf: 'flex-end', padding: 8 },
  viewerImageBox: { width: '100%', maxHeight: 640, alignItems: 'center', justifyContent: 'center' },
  viewerImage: { width: '100%', height: '100%' },
  viewerSpinner: { position: 'absolute' },
  viewerCaption: { color: '#ffffff', fontSize: 16, fontWeight: '600', textAlign: 'center' },
  viewerMeta: { color: '#c3cad4', fontSize: 13 },
  viewerError: { color: '#ff8a80', fontSize: 13 },
  deleteLink: { color: '#ff8a80', fontWeight: '600' },
  dangerButton: { backgroundColor: '#d93838', borderRadius: 10, paddingVertical: 8, paddingHorizontal: 14 },
  dangerText: { color: '#ffffff', fontWeight: '700' },
});
