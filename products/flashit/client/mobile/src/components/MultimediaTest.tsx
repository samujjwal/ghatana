import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';

export const MultimediaTest: React.FC = () => {
    const navigation = useNavigation();
    const [testResults, setTestResults] = useState<string[]>([]);

    const addResult = (message: string) => {
        setTestResults(prev => [...prev, `${new Date().toLocaleTimeString()}: ${message}`]);
    };

    const testNavigation = () => {
        try {
            // @ts-ignore
            navigation.navigate('Capture');
            addResult('✅ Navigation to unified capture works');
        } catch (error) {
            addResult(`❌ Navigation failed: ${error}`);
        }
    };

    const testVoiceNavigation = () => {
        try {
            // @ts-ignore
            navigation.navigate('VoiceRecorder');
            addResult('✅ Voice recorder navigation works');
        } catch (error) {
            addResult(`❌ Voice navigation failed: ${error}`);
        }
    };

    const testImageNavigation = () => {
        try {
            // @ts-ignore
            navigation.navigate('ImageCapture');
            addResult('✅ Image capture navigation works');
        } catch (error) {
            addResult(`❌ Image navigation failed: ${error}`);
        }
    };

    const testVideoNavigation = () => {
        try {
            // @ts-ignore
            navigation.navigate('VideoRecorder');
            addResult('✅ Video recorder navigation works');
        } catch (error) {
            addResult(`❌ Video navigation failed: ${error}`);
        }
    };

    return (
        <View style={styles.container}>
            <Text style={styles.title}>Multimedia Test Suite</Text>

            <View style={styles.buttonContainer}>
                <TouchableOpacity style={styles.button} onPress={testNavigation}>
                    <Text style={styles.buttonText}>Test Unified Capture</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.button} onPress={testVoiceNavigation}>
                    <Text style={styles.buttonText}>Test Voice Recorder</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.button} onPress={testImageNavigation}>
                    <Text style={styles.buttonText}>Test Image Capture</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.button} onPress={testVideoNavigation}>
                    <Text style={styles.buttonText}>Test Video Recorder</Text>
                </TouchableOpacity>
            </View>

            <View style={styles.resultsContainer}>
                <Text style={styles.resultsTitle}>Test Results:</Text>
                {testResults.map((result, index) => (
                    <Text key={index} style={styles.resultText}>{result}</Text>
                ))}
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        padding: 20,
        backgroundColor: '#fff',
    },
    title: {
        fontSize: 24,
        fontWeight: 'bold',
        marginBottom: 20,
        textAlign: 'center',
    },
    buttonContainer: {
        marginBottom: 20,
    },
    button: {
        backgroundColor: '#007aff',
        padding: 15,
        borderRadius: 8,
        marginBottom: 10,
    },
    buttonText: {
        color: '#fff',
        fontSize: 16,
        textAlign: 'center',
    },
    resultsContainer: {
        flex: 1,
        backgroundColor: '#f5f5f5',
        padding: 15,
        borderRadius: 8,
    },
    resultsTitle: {
        fontSize: 18,
        fontWeight: 'bold',
        marginBottom: 10,
    },
    resultText: {
        fontSize: 14,
        marginBottom: 5,
        fontFamily: 'monospace',
    },
});
