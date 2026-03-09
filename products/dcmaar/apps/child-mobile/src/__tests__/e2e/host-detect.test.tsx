import React from 'react';
import { render } from '@testing-library/react-native';
import { View, Text } from 'react-native';

test('host component detection sanity check', () => {
  const utils = render(
    <View>
      <Text>hello</Text>
    </View>
  );
  expect(utils.getByText('hello')).toBeTruthy();
});
