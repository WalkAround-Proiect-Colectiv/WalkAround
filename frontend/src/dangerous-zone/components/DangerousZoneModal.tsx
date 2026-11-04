import React, { useState } from 'react';
import { Modal, View, Text, TextInput, Pressable, StyleSheet, Switch, ActivityIndicator } from 'react-native';
import { DangerousZoneCategory, CATEGORY_LABELS } from '../dangerousZone.types';
import { reportDangerousZone } from '../api/dangerousZoneApi';
import { X } from 'lucide-react-native';

export type DangerousZoneModalProps = {
    visible: boolean;
    onClose: () => void;
    currentLat: number;
    currentLng: number;
    onSuccess: () => void;
};

export default function DangerousZoneModal({ visible, onClose, currentLat, currentLng, onSuccess }: DangerousZoneModalProps) {
    const [selectedCategory, setSelectedCategory] = useState<DangerousZoneCategory>(DangerousZoneCategory.CRIME);
    const [description, setDescription] = useState('');
    const [is247, setIs247] = useState(true);
    const [startTime, setStartTime] = useState('22:00');
    const [endTime, setEndTime] = useState('06:00');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    async function handleSubmit()
    {
        if (!is247)
        {
            const timeRegex = /^([01]\d|2[0-3]):([0-5]\d)$/;
            if (!timeRegex.test(startTime) || !timeRegex.test(endTime))
            {
                setError('Please enter time in valid HH:mm format (e.g., 08:30, 22:00).');
                return;
            }
        }
        setLoading(true);
        setError(null);
        setSuccessMessage(null);
        try
        {
            await reportDangerousZone({
                lat: currentLat,
                lng: currentLng,
                category: selectedCategory,
                description: description.trim() || undefined,
                is24_7: is247,
                startTime: is247 ? undefined : `${startTime}:00`,
                endTime: is247 ? undefined : `${endTime}:00`,
            });
            setSuccessMessage('Dangerous zone reported successfully!');
            setTimeout(() => {
                onSuccess();
                onClose();
                setSuccessMessage(null);
            }, 2000);
        }
        catch (err)
        {
            setError(err instanceof Error ? err.message : 'Failed to submit report. Please try again.');
        }
        finally
        {
            setLoading(false);
        }
    }

    return (
        <Modal visible={visible} animationType="slide" transparent>
            <View style={styles.overlay}>
                <View style={styles.container}>
                    <View style={styles.header}>
                        <Text style={styles.title}>Report Dangerous Zone</Text>
                        <Pressable onPress={onClose} style={styles.closeButton}>
                            <X size={22} color="#363d47" />
                        </Pressable>
                    </View>

                    <Text style={styles.label}>Select Category:</Text>
                    <View style={styles.categoriesContainer}>
                        {Object.values(DangerousZoneCategory).map((cat) => (
                            <Pressable
                                key={cat}
                                style={[styles.categoryChip, selectedCategory === cat && styles.selectedChip]}
                                onPress={() => setSelectedCategory(cat)}
                            >
                                <Text style={[styles.chipText, selectedCategory === cat && styles.selectedChipText]}>
                                    {CATEGORY_LABELS[cat]}
                                </Text>
                            </Pressable>
                        ))}
                    </View>

                    <Text style={styles.label}>Description (optional):</Text>
                    <TextInput
                        style={styles.input}
                        placeholder="Extra details"
                        value={description}
                        onChangeText={setDescription}
                        multiline
                    />

                    <View style={styles.switchRow}>
                        <Text style={styles.label}>Active 24/7?</Text>
                        <Switch value={is247} onValueChange={setIs247} />
                    </View>

                    {!is247 && (
                        <View style={styles.timeRow}>
                            <View style={styles.timeField}>
                                <Text style={styles.label}>From:</Text>
                                <TextInput style={styles.timeInput} value={startTime} onChangeText={setStartTime} placeholder="HH:mm" />
                            </View>
                            <View style={styles.timeField}>
                                <Text style={styles.label}>To:</Text>
                                <TextInput style={styles.timeInput} value={endTime} onChangeText={setEndTime} placeholder="HH:mm" />
                            </View>
                        </View>
                    )}

                    {error && <Text style={styles.errorText}>{error}</Text>}
                    {successMessage && <Text style={styles.successText}>{successMessage}</Text>}
                    {!successMessage && (
                        <Pressable style={styles.submitButton} onPress={handleSubmit} disabled={loading}>
                            {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.submitButtonText}>Submit Report</Text>}
                        </Pressable>
                    )}
                </View>
            </View>
        </Modal>
    );
}

const styles = StyleSheet.create({
    overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
    container: { backgroundColor: '#fff', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, gap: 16 },
    header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    title: { fontSize: 18, fontWeight: '700', color: '#17372b' },
    closeButton: { padding: 4 },
    label: { fontSize: 14, fontWeight: '600', color: '#52635c' },
    categoriesContainer: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    categoryChip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 16, backgroundColor: '#f3f7f5', borderWidth: 1, borderColor: '#dce2e9' },
    selectedChip: { backgroundColor: '#d93838', borderColor: '#d93838' },
    chipText: { fontSize: 13, color: '#363d47' },
    selectedChipText: { color: '#fff', fontWeight: '600' },
    input: { borderWidth: 1, borderColor: '#dce2e9', borderRadius: 8, padding: 12, minHeight: 60, textAlignVertical: 'top' },
    switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    timeRow: { flexDirection: 'row', gap: 16 },
    timeField: { flex: 1, gap: 4 },
    timeInput: { borderWidth: 1, borderColor: '#dce2e9', borderRadius: 8, padding: 8 },
    errorText: { color: '#a32929', fontSize: 13 },
    successText: { color: '#2b8a3e', fontSize: 15, fontWeight: '600', textAlign: 'center', marginVertical: 8 },
    submitButton: { backgroundColor: '#d93838', padding: 16, borderRadius: 12, alignItems: 'center', marginTop: 8 },
    submitButtonText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});