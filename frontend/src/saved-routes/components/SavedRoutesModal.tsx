import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MapPin, Route, Trash2, X } from 'lucide-react-native';
import { authenticatedRequest } from '../../login/api/session';

type SavedRoute = {
    id: number;
    startLat: number;
    startLng: number;
    endLat: number;
    endLng: number;
    createdAt: string;
};

type Props = {
    visible: boolean;
    onClose: () => void;
    onSelectRoute: (destination: { lat: number; lng: number }, routeId: number) => void;
};

export default function SavedRoutesModal({ visible, onClose, onSelectRoute }: Props) {
    const [routes, setRoutes] = useState<SavedRoute[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!visible) return;
        let active = true;
        setLoading(true);
        setError(null);

        authenticatedRequest<SavedRoute[]>('/api/v1/saved-routes', { method: 'GET' })
            .then(data => {
                if (active) {
                    setRoutes(data);
                    setLoading(false);
                }
            })
            .catch(err => {
                if (active) {
                    console.error('Failed to fetch saved routes:', err);
                    setError('Could not load saved routes.');
                    setLoading(false);
                }
            });

        return () => { active = false; };
    }, [visible]);

    async function handleDelete(id: number) {
        try {
            await authenticatedRequest(`/api/v1/saved-routes/${id}`, { method: 'DELETE' });
            setRoutes(prev => prev.filter(r => r.id !== id));
        } catch (err) {
            console.error('Failed to delete route:', err);
        }
    }

    return (
        <Modal visible={visible} animationType="slide" transparent>
            <View style={styles.overlay}>
                <View style={styles.container}>
                    <View style={styles.header}>
                        <View style={styles.titleRow}>
                            <Route size={24} color="#0065EB" />
                            <Text style={styles.title}>My Routes</Text>
                        </View>
                        <Pressable accessibilityRole="button" onPress={onClose} style={styles.closeBtn}>
                            <X size={24} color="#363d47" />
                        </Pressable>
                    </View>

                    {loading ? (
                        <View style={styles.centerContent}>
                            <ActivityIndicator size="large" color="#0065EB" />
                        </View>
                    ) : error ? (
                        <View style={styles.centerContent}>
                            <Text style={styles.errorText}>{error}</Text>
                        </View>
                    ) : routes.length === 0 ? (
                        <View style={styles.centerContent}>
                            <Text style={styles.emptyText}>You have no saved routes.</Text>
                        </View>
                    ) : (
                        <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
                            {routes.map(route => (
                                <View key={route.id} style={styles.card}>
                                    <Pressable
                                        style={styles.cardContent}
                                        onPress={() => onSelectRoute({ lat: route.endLat, lng: route.endLng }, route.id)}
                                    >
                                        <MapPin size={20} color="#d93838" style={styles.icon} />
                                        <View style={styles.textContainer}>
                                            <Text style={styles.destText}>
                                                Destination: {route.endLat.toFixed(4)}, {route.endLng.toFixed(4)}
                                            </Text>
                                            <Text style={styles.dateText}>
                                                Saved: {new Date(route.createdAt).toLocaleDateString()}
                                            </Text>
                                        </View>
                                    </Pressable>
                                    <Pressable
                                        accessibilityRole="button"
                                        style={styles.deleteBtn}
                                        onPress={() => handleDelete(route.id)}
                                    >
                                        <Trash2 size={20} color="#758091" />
                                    </Pressable>
                                </View>
                            ))}
                        </ScrollView>
                    )}
                </View>
            </View>
        </Modal>
    );
}

const styles = StyleSheet.create({
    overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
    container: { backgroundColor: '#ffffff', borderTopLeftRadius: 24, borderTopRightRadius: 24, maxHeight: '80%', minHeight: '50%', paddingBottom: 20, elevation: 20, shadowColor: '#000', shadowOffset: { width: 0, height: -4 }, shadowOpacity: 0.1, shadowRadius: 10 },
    header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20, borderBottomWidth: 1, borderBottomColor: '#e5e8ed' },
    titleRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
    title: { fontSize: 20, fontWeight: '700', color: '#263241' },
    closeBtn: { padding: 4 },
    centerContent: { flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 200 },
    errorText: { color: '#a60000', fontSize: 15, fontWeight: '600' },
    emptyText: { color: '#758091', fontSize: 15, fontWeight: '500' },
    list: { flexShrink: 1 },
    listContent: { padding: 16, gap: 12 },
    card: { flexDirection: 'row', backgroundColor: '#f9f9fb', borderRadius: 16, borderWidth: 1, borderColor: '#dce2e9', overflow: 'hidden' },
    cardContent: { flex: 1, flexDirection: 'row', alignItems: 'center', padding: 16, gap: 12 },
    icon: { flexShrink: 0 },
    textContainer: { flex: 1 },
    destText: { fontSize: 15, fontWeight: '600', color: '#263241', marginBottom: 4 },
    dateText: { fontSize: 13, color: '#758091' },
    deleteBtn: { padding: 16, justifyContent: 'center', alignItems: 'center', borderLeftWidth: 1, borderLeftColor: '#dce2e9', backgroundColor: '#fff' }
});